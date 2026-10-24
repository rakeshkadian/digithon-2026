package com.copilot.llm;

import com.copilot.model.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends schema + code context (never row values) to the LLM and asks for a JSON
 * classification of every column. One call per entity keeps the prompt small.
 */
@Slf4j
@Service
public class LlmClassifier {

    private static final String SYSTEM = """
        You are a data-privacy analyst for India's DPDP Act 2023.
        You receive the schema of one database table from a Java Spring Boot application,
        plus the places in code that use each column.
        Classify every column.

        PI types: FULL_NAME, FIRST_NAME, LAST_NAME, EMAIL, PHONE, AADHAAR, PAN, ADDRESS,
        DATE_OF_BIRTH, BANK_ACCOUNT, SALARY, GOVERNMENT_ID, EMPLOYEE_ID, OTHER_PI, NOT_PI.

        Rules:
        - Primary keys and technical columns (id, created_at, version) are NOT_PI.
        - A value that identifies a person on its own, or together with the other columns
          of this table, is PI.
        - Columns like department or date_of_joining are NOT_PI alone but mark them
          confidence 20-40 with reason "identifying only when joined with a person row".
        - recommendation: ENCRYPT_WITH_BLIND_INDEX if the column is used in a lookup
          (findBy..., WHERE), ENCRYPT if PI but never searched, REVIEW if unsure, NONE if NOT_PI.
        - confidence is an integer 0-100.

        Respond with ONLY a JSON array, no prose, no markdown fences:
        [{"column":"...","piType":"...","confidence":90,"reason":"...","recommendation":"..."}]
        """;

    private final ChatClient chat;
    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean replay;
    private final Path replayDir;
    private final Path cacheDir;

    public LlmClassifier(ChatClient.Builder builder,
                         @Value("${copilot.llm-replay}") boolean replay,
                         @Value("${copilot.workdir}") String workdir) {
        this.chat = builder.defaultSystem(SYSTEM).build();
        this.replay = replay;
        this.replayDir = Path.of(workdir, "llm-replay");
        this.cacheDir = Path.of(workdir, "llm-cache");
    }

    public List<Finding> classify(RepoSnapshot snap) {
        List<Finding> all = new ArrayList<>();
        for (EntityInfo entity : snap.getEntities()) {
            all.addAll(classifyEntity(entity, snap));
        }
        return all;
    }

    private List<Finding> classifyEntity(EntityInfo entity, RepoSnapshot snap) {
        String prompt = buildPrompt(entity, snap);
        String json = replay ? readReplay(entity) : cachedLlm(prompt, entity, snap);
        List<LlmRow> rows = parse(json);

        List<Finding> out = new ArrayList<>();
        for (FieldInfo f : entity.getFields()) {
            LlmRow row = rows.stream().filter(r -> f.getColumnName().equalsIgnoreCase(r.column)
                                               || f.getJavaName().equalsIgnoreCase(r.column))
                    .findFirst().orElse(null);
            String key = entity.getTableName() + "." + f.getColumnName();
            out.add(Finding.builder()
                    .table(entity.getTableName())
                    .column(f.getColumnName())
                    .javaField(f.getJavaName())
                    .piType(row == null ? "REVIEW" : row.piType)
                    .confidence(row == null ? 50 : row.confidence)
                    .reason(row == null ? "LLM gave no answer for this column" : row.reason)
                    .recommendation(row == null ? "REVIEW" : row.recommendation)
                    .codeRefs(snap.getCodeRefs().getOrDefault(key, List.of()))
                    .build());
        }
        return out;
    }

    // ------------------------------------------------------------------ prompt

    String buildPrompt(EntityInfo entity, RepoSnapshot snap) {
        StringBuilder sb = new StringBuilder();
        sb.append("Table: ").append(entity.getTableName())
          .append(" (Java entity ").append(entity.getClassName()).append(")\n\nColumns:\n");
        for (FieldInfo f : entity.getFields()) {
            String key = entity.getTableName() + "." + f.getColumnName();
            List<CodeRef> refs = snap.getCodeRefs().getOrDefault(key, List.of());
            sb.append("- ").append(f.getColumnName())
              .append(" | java field ").append(f.getJavaName())
              .append(" | type ").append(f.getJavaType())
              .append(f.isId() ? " | PRIMARY KEY" : "")
              .append(f.getLength() != null ? " | length " + f.getLength() : "")
              .append("\n    used in: ");
            if (refs.size() <= 1) sb.append("entity only");
            else sb.append(String.join(", ", refs.stream().skip(1).limit(8)
                    .map(r -> r.getClassName() + "." + r.getMethodName() + " [" + r.getKind() + "]").toList()));
            sb.append("\n");
        }
        if (!snap.getRepositories().isEmpty()) {
            sb.append("\nRepository @Query strings:\n");
            snap.getRepositories().forEach(r -> r.getQueries().forEach(q -> sb.append("  ").append(q).append("\n")));
        }
        sb.append("\nClassify every column listed above.");
        return sb.toString();
    }

    // ------------------------------------------------------------------ LLM call + parsing

    private String cachedLlm(String prompt, EntityInfo entity, RepoSnapshot snap) {
        Path cacheFile = cacheDir.resolve(cacheKey(snap.getRepoUrl(), snap.getBranch(), entity.getTableName()) + ".json");
        synchronized (cacheFile.toAbsolutePath().toString().intern()) {
            try {
                if (Files.exists(cacheFile)) {
                    log.info("Using cached LLM classification for {}/{}", snap.getRepoUrl(), entity.getTableName());
                    return Files.readString(cacheFile);
                }
            } catch (IOException e) {
                log.warn("Could not read LLM cache for {}: {}", entity.getTableName(), e.getMessage());
            }

            String answer = callLlm(prompt, entity);
            try {
                Files.createDirectories(cacheDir);
                Files.writeString(cacheFile, answer);
            } catch (IOException e) {
                log.warn("Could not write LLM cache for {}: {}", entity.getTableName(), e.getMessage());
            }
            return answer;
        }
    }
    private String cacheKey(String repoUrl, String branch, String tableName) {
        String repository = repoUrl == null ? "unknown" : repoUrl.trim().replaceAll("\\.git$", "").toLowerCase();
        String identity = repository + "\n" + (branch == null ? "main" : branch) + "\n" + tableName.toLowerCase();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
    private String callLlm(String prompt, EntityInfo entity) {
        log.debug("LLM prompt for {}:\n{}", entity.getTableName(), prompt);
        String answer = chat.prompt().user(prompt).call().content();
        log.debug("LLM answer:\n{}", answer);
        saveReplay(entity, answer);          // so the demo can run offline next time
        return answer;
    }

    private List<LlmRow> parse(String raw) {
        if (raw == null) return List.of();
        String clean = raw.trim().replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
        int start = clean.indexOf('['), end = clean.lastIndexOf(']');
        if (start >= 0 && end > start) clean = clean.substring(start, end + 1);
        try {
            return mapper.readValue(clean, new TypeReference<List<LlmRow>>() {});
        } catch (IOException e) {
            log.warn("Could not parse LLM JSON: {}", e.getMessage());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LlmRow {
        public String column;
        public String piType;
        public int confidence;
        public String reason;
        public String recommendation;
    }

    // ------------------------------------------------------------------ replay (demo safety net)

    private void saveReplay(EntityInfo entity, String answer) {
        try {
            Files.createDirectories(replayDir);
            Files.writeString(replayDir.resolve(entity.getTableName() + ".json"), answer);
        } catch (IOException e) {
            log.warn("Could not save replay file: {}", e.getMessage());
        }
    }

    private String readReplay(EntityInfo entity) {
        try {
            return Files.readString(replayDir.resolve(entity.getTableName() + ".json"));
        } catch (IOException e) {
            log.error("Replay enabled but no file for table {}", entity.getTableName());
            return "[]";
        }
    }
}
