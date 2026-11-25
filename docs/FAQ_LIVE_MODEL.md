# FAQ live single-turn verification

## Corpus update (11 November 2026)

The local knowledge table now contains40 reviewed entries after35 additive inserts, with all original five fields/IDs/timestamps preserved. This batch made zero provider calls and changed no model/C5 configuration. Its228 passing backend tests, real MySQL canonical readback and offline retrieval/gate benchmark are documented in [FAQ_CORPUS_BASELINE.md](FAQ_CORPUS_BASELINE.md). The paid/live results below used the earlier five-entry corpus; do not treat them as a full real-generation evaluation of the expanded corpus. A fresh live evaluation requires its own scope and budget.

## Current C5 multi-turn acceptance (10 November 2026)

C5 now uses bounded English reference/ellipsis rules to gate an optional rewrite, then retrieves current MySQL evidence and generates a cited answer. Explicit independent questions skip rewriting. Only up to three current-topic questions/outcome modes enter the rewrite request; old assistant answer text is never sent. Resolved questions retain the topic through chained references. Topic markers exclude earlier unrelated topics while preserving the three-exchange storage bound.

Chat additionally returns `mode=clarify`, `matched=false`, no answer/sources and fixed English guidance. The page labels it **Please clarify**. The original `/faq/ask` still has three modes. Missing/ambiguous context or failed rewriting cannot become a guessed answer. A successful stored answer returned after a generation deadline retains the newly established topic; unfinished rewriting does not enter history. One rewrite (128 output tokens) and one generation (512) share the existing35-second deadline; no automatic retry was added.

Verification:

- Full backend regression: **223 passed**, compilation passed. Frontend: **48 passed**, production build passed, with existing dependency annotation warnings.
- New behavior tests first failed against single-turn behavior. An independent review found omitted ordinary `this`/`Who can I contact?` references and lost topic metadata on extractive timeouts. Four regression cases reproduced the findings and the final full suite passed after fixing them. There was no second independent review.
- `FaqLiveMultiTurnIT`: real MySQL and DeepSeek, synthetic authentication, **six** provider requests. Course selection → phone follow-up → calendar topic switch → `it` follow-up all generated correct cited facts. No-match, references after no-match and references without context required no additional model call.
- Real browser: explicitly authorized `stu1` sign-in, no-context clarification, acknowledged New conversation, Student Affairs Service Hall → `Who can I contact?`. Both factual turns showed **027-67865223**, `[FAQ:5]` and the official source. These used **three** more provider calls.
- Total for this batch: **nine** requests within the user-approved ten-request/US$0.05 cap. Exact invoice usage was not inspected; previous provider-validation budgets are separate. The current [official price table](https://api-docs.deepseek.com/quick_start/pricing/) was checked before the paid smoke test. No database writes or dependency changes.

Evidence: `springboot/target/faq-c5-multiturn/{red,guards-red,review-red,final-tests,final-compile,frontend-red,frontend-tests,frontend-build,live-disabled,live-it}.log`, [follow-up screenshot](../report-screenshots/faq-c5-follow-up-20261110.jpg) and [clarification screenshot](../report-screenshots/faq-c5-clarification-20261110.jpg). The task-owned backend was restarted with the existing `faq-deepseek` profile; start instructions below still apply.

To explicitly repeat the paid six-call check, first obtain a fresh verification budget and load the private `DEEPSEEK_API_KEY` as shown below, then from `springboot` run:

```powershell
$env:FAQ_C5_LIVE_VERIFY = 'true'
try {
    mvn '-Dtest=FaqLiveMultiTurnIT' test
} finally {
    Remove-Item Env:FAQ_C5_LIVE_VERIFY -ErrorAction SilentlyContinue
}
```

The IT is excluded from ordinary `mvn test` and skipped unless explicitly enabled. It checks the exact DeepSeek endpoint/model/private-key source and caps its transport at six requests. A failed paid check can consume calls; do not blindly rerun it. The synthetic Session does not replace browser sign-in evidence.

Limitations: the conservative English gate is not a general language parser. Uncovered ellipsis may return no match, and a complete first question containing an internal pronoun can request clarification. Rephrasing explicitly is the current workaround. Citation validation does not prove factual support. R4 embeddings, corpus expansion and the independent30-question evaluation remain pending; C5's30 controlled backend cases are not that evaluation set. Earlier single-turn records below are historical.

## Current provider: DeepSeek (10 November 2026)

The running backend now uses the official `deepseek-flash` model with thinking disabled, JSON output and `max_tokens=512`. The user approved up to three real verification requests and a US$0.01 verification budget, and configured `DEEPSEEK_API_KEY` privately. This budget covers agent verification only; subsequent manual application requests are billed by DeepSeek. No automatic retry or provider switching was added.

From the repository's `springboot` directory, start DeepSeek with:

```powershell
if (-not $env:DEEPSEEK_API_KEY) {
    $env:DEEPSEEK_API_KEY = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'User')
}
if ([string]::IsNullOrWhiteSpace($env:DEEPSEEK_API_KEY)) {
    throw 'Set the private DEEPSEEK_API_KEY first.'
}
mvn spring-boot:run '-Dspring-boot.run.profiles=faq-deepseek'
```

The `faq-deepseek` profile uses the **separate** `faq.deepseek.*` namespace. `DEEPSEEK_BASE_URL` defaults to `https://api.deepseek.com` and `DEEPSEEK_MODEL` to `deepseek-flash`; the output parameter is `max_tokens`. Old `FAQ_LLM_API_KEY`, `FAQ_LLM_BASE_URL` and `FAQ_LLM_MODEL` aliases cannot override this namespace. `FAQ_LLM_ENABLED=false` remains an intentional way to disable generation. Starting without a model profile still leaves generation disabled.

Verification: 193 backend regression tests and compilation passed. Review identified Spring environment-alias precedence that could override a plain profile; a real `SystemEnvironmentPropertySource` regression reproduced it, then the namespace fix passed the full suite. No second independent review was performed. Three real requests succeeded (two browser checks and one final real Service check); the last Service request was 1,347 serialized bytes with a 512-token output limit. The telephone `027-67863133`, inline `[FAQ:4]` citation and official contact-page source were verified; no-match handling also passed. Exact billed usage was not inspected. Frontend code and dependencies were unchanged, so the earlier frontend suite was not repeated.

Evidence: `springboot/target/faq-deepseek/{red,environment-red,final-tests,final-compile,live-final}.log` and the [final DeepSeek browser screenshot](../report-screenshots/faq-deepseek-generated-20261110.jpg). The backend is currently running with this profile. No business data or SQL scripts were changed, and no Git commit was made.

Official references: [current API model and endpoint](https://api-docs.deepseek.com/), [thinking switch](https://api-docs.deepseek.com/guides/thinking_mode/), [JSON output](https://api-docs.deepseek.com/guides/json_mode/), [pricing](https://api-docs.deepseek.com/quick_start/pricing/). The GLM instructions and evidence below are retained for the previous provider; use `faq-glm` explicitly if switching back.

The existing chat page calls `/api/faq/chat`. At the earlier single-turn milestone, stored turns were not used for rewriting. The current C5 behavior is documented above. TF-IDF still retrieves up to three enabled CCNU entries, the model writes an English answer, and the backend validates citation IDs and maps sources from MySQL.

## Start the free GLM model (previous provider)

The explicit `faq-glm` Spring profile selects `glm-4-flash-250414`, `https://open.bigmodel.cn/api/paas/v4`, and `max_tokens=512`. This model is listed as free in the [official price table](https://docs.bigmodel.cn/cn/guide/start/pricing). GLM-4.7-Flash returned HTTP 429 / provider code 1305 (model overloaded) during acceptance, so the tested default uses the other free model. There is no automatic model switching. The ordinary application still starts with generation disabled. No new SDK or database migration is needed.

An explicit `FAQ_LLM_MODEL=glm-4.7-flash` remains supported; the client disables thinking for that model to reserve the output budget for the answer. The system prompt includes a concrete JSON example using an ID from the current evidence; all existing citation and link validation remains mandatory.

Create a **standard API** key in the [BigModel console](https://bigmodel.cn/usercenter/proj-mgmt/apikeys). Keep it in the Windows user environment variable `FAQ_LLM_API_KEY`; never paste it into chat, source files, screenshots or logs. This guide does not require a paid Coding Plan.

In a PowerShell terminal, from the repository's `springboot` directory:

```powershell
# Refresh a user variable added after this terminal/app started; do not print it.
if (-not $env:FAQ_LLM_API_KEY) {
    $env:FAQ_LLM_API_KEY = [Environment]::GetEnvironmentVariable('FAQ_LLM_API_KEY', 'User')
}
if ([string]::IsNullOrWhiteSpace($env:FAQ_LLM_API_KEY)) {
    throw 'Set FAQ_LLM_API_KEY in your private backend environment first.'
}
mvn spring-boot:run '-Dspring-boot.run.profiles=faq-glm'
```

Keep the existing MySQL connection settings. Do not reimport SQL or replace accounts to run this check. If port 9090 already serves this project, restart that backend with the profile after stopping it normally; do not kill unrelated processes.

`FAQ_LLM_ENABLED`, `FAQ_LLM_BASE_URL`, `FAQ_LLM_MODEL`, and `FAQ_LLM_OUTPUT_TOKEN_PARAMETER` can override the profile defaults. Check these variables if configuration behaves unexpectedly. Disabling generation requires `FAQ_LLM_ENABLED=false` or starting without the profile. The application checks credentials at startup but a successful startup does not prove the key works with the provider.

## Explicit live API smoke check

This check reads the existing database and makes **two** real generation calls to the configured free Flash model through the current controller, retriever, client, validation and source mapping. Its authentication context is synthetic; it does not replace a browser sign-in test. It verifies the calendar year, the course-selection telephone number, stored source titles/URLs, and an unrelated no-match question. It does not update business records.

From `springboot`, with the key available as above:

```powershell
$env:FAQ_LIVE_VERIFY = 'true'
try {
    mvn '-Dtest=FaqLiveModelIT' test
} finally {
    Remove-Item Env:FAQ_LIVE_VERIFY -ErrorAction SilentlyContinue
}
```

The opt-in check is excluded from normal `mvn test`; even when explicitly selected it is skipped unless `FAQ_LIVE_VERIFY=true`. It permits only `glm-4-flash-250414` or `glm-4.7-flash` at the exact official endpoint to avoid accidentally testing a paid model. `extractive` is a failure for its positive cases, not evidence of a successful live generation. Printed `LIVE` lines contain only public result fields and elapsed time; do not enable HTTP header/body debug logging with real credentials.

## Browser acceptance

Start the Vue frontend normally, open `/faq`, and sign in with an existing demo account. Check:

1. `Where can I find the CCNU academic calendar for 2026-2027?` produces **AI-generated answer**, the correct academic year and a source from the database.
2. `Who can I contact about undergraduate course selection?` preserves `027-67863133` and shows the corresponding official source. Manually check all claims against the stored evidence.
3. `volcano eruption` produces **No matching answer**. No evidence must never become a general model answer.
4. With generation disabled, a supported question produces **Stored answer** and an explicit fallback notice.
5. New conversation clears only after server acknowledgement; expired login requires sign-in. Stop ends UI waiting and does not promise upstream cancellation.

The backend retains its 35-second business budget and the frontend its FAQ-only 45-second timeout. Existing controlled regressions cover provider timeouts, 429, invalid JSON, truncated answers and invalid citations; they are separate from real-provider evidence.

## Acceptance status

2026-11-10: **basic real single-turn acceptance passed** with `glm-4-flash-250414`, the existing MySQL knowledge, and the real browser page.

- Backend regression: 191 passed; compilation passed. Frontend: 46 passed; production build passed. An independent read-only review found no blocking issue, including the later prompt/default-model change.
- The explicit live test passed calendar generation (1,766 ms), course-selection generation (1,373 ms), stored source-title/URL checks, and no-match handling. These are smoke-test timings, not performance benchmarks.
- A real Student `stu1` browser login was explicitly authorized by the user. Before the model switch, real overload produced a labelled Stored answer and an unrelated question produced No matching answer. After restarting with the tested free model, the browser displayed AI-generated answer, `027-67863133`, `[FAQ:4]`, and the official contact-page link.
- MySQL84 was initially stopped; the agent could not start the Windows service, and the user started it. The agent did not import SQL or alter business records. The API key was loaded from the private Windows user environment, never placed in source or command arguments.
- The original five-request estimate grew to eight manually triggered model requests while investigating overload and missing inline citations. All requests selected one of the two officially free model IDs. Normal application requests still make at most one model call with no automatic retry.
- Early real tests correctly failed on fallback or missing inline citation; they were not counted as successful generation. The final prompt adds a valid output example while keeping the existing validator unchanged.

Reproducible evidence is in `springboot/target/faq-live-single-turn/`: `red.log`, `grounding-red.log`, `final-tests.log`, `live-it.log` (initial failure), `provider-probe.log` (429/1305), `provider-free-flash-probe.log` (200, missing marker), and `live-final-it.log` (passed). Browser proof: [real generated answer](../report-screenshots/faq-live-generated-20261110.jpg).

This completes the basic single-turn connection, not full FAQ quality evaluation. Corpus expansion, semantic retrieval, multi-turn understanding and the 30-question final evaluation remain separate work; module status stays **Under development**. The private key remains required in each backend runtime environment.

Protocol references: [GLM-4.7-Flash](https://docs.bigmodel.cn/cn/guide/models/free/glm-4.7-flash), [thinking control](https://docs.bigmodel.cn/cn/guide/capabilities/thinking-mode), [JSON output](https://docs.bigmodel.cn/cn/guide/capabilities/struct-output).
