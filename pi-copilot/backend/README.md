# PI Security Co-Pilot – backend (MVP slice 1: clone → analyze → classify)

## Run

```bash
# OpenAI (set your API key before starting)
$env:OPENAI_API_KEY="sk-..."
# Linux/macOS: export OPENAI_API_KEY=sk-...

mvn spring-boot:run
```

## Try it

```bash
curl -X POST localhost:8081/api/scans -H 'Content-Type: application/json' \
  -d '{"repoUrl":"https://github.com/<org>/employee-app.git","branch":"main","token":"<PAT>"}'
# -> {"scanId":"...","status":"QUEUED"}

curl localhost:8081/api/scans/<scanId>            # poll until status = DONE
curl localhost:8081/api/scans/<scanId>/findings   # table for the Angular UI
```

## Demo safety net

LLM classifications are cached in `/tmp/copilot-repos/llm-cache/` by repository URL, branch, and table. Repeat scans reuse the cached answer without another API call.
Start with `COPILOT_LLM_REPLAY=true` to reuse it with no network.

## Package layout

| Package | Class | Role |
|---|---|---|
| git | GitCloneService | shallow clone with token, cleanup |
| analyzer | RepoAnalyzer | JavaParser: entities, repositories, controllers, services, DTOs, code refs, datasource config |
| llm | LlmClassifier | Spring AI prompt + JSON parsing + replay |
| scan | ScanService / ScanController | async job, REST API |
| model | RepoSnapshot, Finding, ... | data passed between steps |

## Next slices
1. Persist `ScanJob` and `Finding` in Postgres (JPA) and add `PATCH /findings/{id}` approve/reject.
2. Remediation: templates → Flyway SQL + Java files → branch + PR.
3. Backfill + AES-GCM + feature flag.
