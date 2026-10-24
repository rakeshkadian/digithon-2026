# PI Security Co-Pilot — MVP (Samsung Digithon 2026)

AI finds personal data in a Spring Boot application's code and database, generates additive,
reversible encryption changes as a pull request, encrypts the data in batches, and switches with a flag.

```
pi-copilot/
├── backend/    Spring Boot 3, Java 17, PostgreSQL, Spring AI, JGit, JavaParser   (port 8081)
├── frontend/   Angular 17 + Angular Material                                       (port 4200)
└── docker-compose.yml   Postgres for the Co-Pilot (port 5433)
```

## 1. Start

```bash
docker compose up -d                                   # Co-Pilot database

# LLM: local Ollama (private) ...
ollama pull llama3.1
export LLM_BASE_URL=http://localhost:11434 LLM_API_KEY=ollama LLM_MODEL=llama3.1
# ... or OpenAI
# export LLM_BASE_URL=https://api.openai.com LLM_API_KEY=sk-... LLM_MODEL=gpt-4o-mini

export COPILOT_DB_URL=jdbc:postgresql://localhost:5433/copilot
export PI_MASTER_KEY=$(openssl rand -base64 32)         # SAME value goes into the Employee App
cd backend && mvn spring-boot:run

cd ../frontend && npm install && npm start              # http://localhost:4200
```

## 2. Demo flow

| Step | Screen | What happens |
|---|---|---|
| Connect | `/` | Repo URL + branch + fine-grained token. Shallow clone. |
| Scan | `/scans/:id` | JavaParser buckets classes (entity, repository, controller, service, DTO), builds code refs, reads `application.yml`. LLM classifies every column → JSON. |
| Review | same | Approve / reject per column; code refs expand per row. |
| Generate | `/plans/:id` | Flyway SQL + `PiCrypto`, `EncryptedStringConverter`, `PiFlags`, `PiFlagController`, `<Entity>PiLookupService`; entity gets `*Enc`/`*Bidx`/`piKeyVer` fields and a `@PrePersist/@PreUpdate` dual-write; repository gets `findBy<Field>Bidx`. Branch pushed, PR opened. |
| Encrypt | `/plans/:id/rollout` | Enter target DB; SQL applied (IF NOT EXISTS); batched backfill; verify (no NULLs + decrypt 50 random rows). |
| Switch | same | Deploy the merged PR with `PI_MASTER_KEY`; toggle `pi.encryption` via the proxied `/pi/flag`; toggle back = rollback. |
| Evidence | `/audit` | Every action logged. |

## 3. REST API (backend)

```
POST  /api/scans                         {repoUrl, branch, token}
GET   /api/scans/{id}                    status + snapshotJson
GET   /api/scans/{id}/findings
PATCH /api/findings/{id}                 {status: APPROVED|REJECTED, comment}
POST  /api/plans                         {scanId, token, openPullRequest}
GET   /api/plans/{id}                    sqlText, filesJson (path, action, content, diff), prUrl
POST  /api/db/test                       {jdbcUrl, username, password}
POST  /api/plans/{id}/backfill           {jdbcUrl, username, password, batchSize}
GET   /api/flags?targetBaseUrl=...       proxied GET  {app}/pi/flag
POST  /api/flags                         {targetBaseUrl, encryption}  → POST {app}/pi/flag
GET   /api/audit
```

## 4. Demo safety

* First LLM answer per table is saved under `$COPILOT_WORKDIR/llm-replay/`. Run with `COPILOT_LLM_REPLAY=true` to replay offline.
* Seed the Employee DB with ~1,000 rows so the backfill progress bar is visible.
* Keep a `psql` window open: `SELECT email, email_enc, email_bidx FROM employee LIMIT 3;`

## 5. Known MVP limits (say them before the judges do)

* Code refs are name-based (JavaParser AST, no data-flow). Good for one entity, noisy on huge repos.
* The flag switches lookups routed through `<Entity>PiLookupService`; existing callers of `findByEmail` keep using the plain column until routed. The PR body says so.
* One key version, key from env var. Vault/KMS provider is the roadmap item.
* Retire step (drop plain columns) is intentionally not automated.

## 6. Build notes

This code was written without a compiler in the loop. Expect a handful of small fixes on first
`mvn compile` / `ng build` (import names, library versions). The libraries and APIs used are stable:
Spring Boot 3.3, Spring AI 1.0 (`ChatClient`), JGit 6.10, JavaParser 3.26, java-diff-utils 4.12,
Angular 17 standalone components with Material.
