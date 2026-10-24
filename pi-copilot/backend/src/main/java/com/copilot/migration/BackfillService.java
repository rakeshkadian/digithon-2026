package com.copilot.migration;

import com.copilot.crypto.PiCrypto;
import com.copilot.persistence.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Applies the generated DDL to the target DB (idempotent, IF NOT EXISTS), then copies
 * plain values into *_enc / *_bidx in batches, then verifies.
 * The DB credentials are used for this job only and never stored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackfillService {

    private final Repositories.PlanRepo planRepo;
    private final Repositories.FindingRepo findingRepo;
    private final PiCrypto crypto;
    private final AuditService audit;
    private final BackfillRunner runner;

    public record DbRequest(String jdbcUrl, String username, String password, int batchSize) {}

    public record TestResult(boolean ok, String message, List<String> tables) {}

    public TestResult testConnection(DbRequest db) {
        try (Connection c = DriverManager.getConnection(db.jdbcUrl(), db.username(), db.password());
             ResultSet rs = c.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            List<String> tables = new ArrayList<>();
            while (rs.next()) tables.add(rs.getString("TABLE_NAME"));
            return new TestResult(true, "Connected. " + tables.size() + " tables.", tables);
        } catch (SQLException e) {
            return new TestResult(false, e.getMessage(), List.of());
        }
    }

    public PlanEntity start(String planId, DbRequest db) {
        PlanEntity plan = planRepo.findById(planId).orElseThrow();
        plan.setStatus("BACKFILL_RUNNING");
        plan.setRowsDone(0);
        plan.setRowsVerified(0);
        planRepo.save(plan);
        audit.log("BACKFILL_STARTED", planId, db.jdbcUrl());
        runner.run(planId, db, crypto);
        return plan;
    }

    @Slf4j
    @Service
    @RequiredArgsConstructor
    public static class BackfillRunner {
        private final Repositories.PlanRepo planRepo;
        private final Repositories.FindingRepo findingRepo;
        private final AuditService audit;

        @Async
        public void run(String planId, DbRequest db, PiCrypto crypto) {
            PlanEntity plan = planRepo.findById(planId).orElseThrow();
            List<FindingEntity> approved = findingRepo.findByScanIdAndStatus(plan.getScanId(), "APPROVED");
            Map<String, List<FindingEntity>> byTable = approved.stream()
                    .collect(Collectors.groupingBy(FindingEntity::getTableName));
            int batch = db.batchSize() <= 0 ? 500 : db.batchSize();

            try (Connection c = DriverManager.getConnection(db.jdbcUrl(), db.username(), db.password())) {
                c.setAutoCommit(false);

                // 1. schema: run the generated SQL (IF NOT EXISTS makes it safe to re-run)
                try (Statement st = c.createStatement()) {
                    for (String stmt : plan.getSqlText().split(";")) {
                        String s = stmt.replaceAll("--.*", "").trim();
                        if (!s.isEmpty()) st.execute(s);
                    }
                }
                c.commit();

                // 2. count
                long total = 0;
                for (String table : byTable.keySet()) total += count(c, "SELECT COUNT(*) FROM " + table);
                plan.setRowsTotal(total);
                planRepo.save(plan);

                // 3. backfill table by table, batch by batch
                for (var e : byTable.entrySet()) {
                    String table = e.getKey();
                    List<FindingEntity> cols = e.getValue();
                    String pk = primaryKey(c, table);
                    String firstEnc = cols.get(0).getColumnName() + "_enc";

                    String selectCols = cols.stream().map(FindingEntity::getColumnName).collect(Collectors.joining(", "));
                    String select = "SELECT " + pk + ", " + selectCols + " FROM " + table
                            + " WHERE " + firstEnc + " IS NULL ORDER BY " + pk + " LIMIT " + batch;

                    StringBuilder set = new StringBuilder();
                    for (FindingEntity f : cols) {
                        set.append(f.getColumnName()).append("_enc = ?, ");
                        if ("ENCRYPT_WITH_BLIND_INDEX".equals(f.getRecommendation()))
                            set.append(f.getColumnName()).append("_bidx = ?, ");
                    }
                    set.append("pi_key_ver = ?");
                    String update = "UPDATE " + table + " SET " + set + " WHERE " + pk + " = ?";

                    while (true) {
                        List<Object[]> rows = new ArrayList<>();
                        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(select)) {
                            while (rs.next()) {
                                Object[] r = new Object[1 + cols.size()];
                                r[0] = rs.getObject(1);
                                for (int i = 0; i < cols.size(); i++) r[1 + i] = rs.getString(2 + i);
                                rows.add(r);
                            }
                        }
                        if (rows.isEmpty()) break;

                        try (PreparedStatement ps = c.prepareStatement(update)) {
                            for (Object[] r : rows) {
                                int p = 1;
                                for (int i = 0; i < cols.size(); i++) {
                                    String plain = (String) r[1 + i];
                                    // write empty-marker instead of NULL so the loop terminates for NULL source values
                                    ps.setBytes(p++, crypto.encrypt(plain == null ? "" : plain));
                                    if ("ENCRYPT_WITH_BLIND_INDEX".equals(cols.get(i).getRecommendation()))
                                        ps.setString(p++, plain == null ? null : crypto.blindIndex(plain));
                                }
                                ps.setInt(p++, PiCrypto.KEY_VERSION);
                                ps.setObject(p, r[0]);
                                ps.addBatch();
                            }
                            ps.executeBatch();
                        }
                        c.commit();
                        plan.setRowsDone(plan.getRowsDone() + rows.size());
                        planRepo.save(plan);
                        log.info("Backfill {}: {}/{}", table, plan.getRowsDone(), plan.getRowsTotal());
                    }
                }
                plan.setStatus("BACKFILL_DONE");
                planRepo.save(plan);

                // 4. verify: no NULL enc column left + decrypt sample
                long verified = 0;
                for (var e : byTable.entrySet()) {
                    String table = e.getKey();
                    FindingEntity f = e.getValue().get(0);
                    long missing = count(c, "SELECT COUNT(*) FROM " + table + " WHERE " + f.getColumnName() + "_enc IS NULL");
                    if (missing > 0) throw new IllegalStateException(missing + " rows in " + table + " still unencrypted");
                    try (Statement st = c.createStatement();
                         ResultSet rs = st.executeQuery("SELECT " + f.getColumnName() + ", " + f.getColumnName() + "_enc FROM "
                                 + table + " ORDER BY random() LIMIT 50")) {
                        while (rs.next()) {
                            String plain = rs.getString(1);
                            String dec = crypto.decrypt(rs.getBytes(2));
                            if (!Objects.equals(plain == null ? "" : plain, dec))
                                throw new IllegalStateException("decrypt mismatch in " + table + "." + f.getColumnName());
                            verified++;
                        }
                    }
                }
                plan.setRowsVerified(verified);
                plan.setStatus("VERIFIED");
                plan.setMessage("Backfill complete. " + plan.getRowsDone() + " rows encrypted, " + verified + " sample rows decrypted OK.");
                planRepo.save(plan);
                audit.log("BACKFILL_VERIFIED", planId, plan.getMessage());
            } catch (Exception ex) {
                log.error("Backfill {} failed", planId, ex);
                plan.setStatus("FAILED");
                plan.setMessage(ex.getMessage());
                planRepo.save(plan);
                audit.log("BACKFILL_FAILED", planId, ex.getMessage());
            }
        }

        private static long count(Connection c, String sql) throws SQLException {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                rs.next();
                return rs.getLong(1);
            }
        }

        private static String primaryKey(Connection c, String table) throws SQLException {
            try (ResultSet rs = c.getMetaData().getPrimaryKeys(null, null, table)) {
                if (rs.next()) return rs.getString("COLUMN_NAME");
            }
            return "id";
        }
    }
}
