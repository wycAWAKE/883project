# M2 course recommendation backend

This delivery adds the course catalogue, prerequisite checks, local TF-IDF recommendations, optional DeepSeek recommendations, and personal recommendation history. Start with the [Java module guide](../../springboot/src/main/java/com/example/springboot/m2/README.md). The team frontend can use the API below.

## Import the catalogue

1. Select the intended MySQL 8 database and back up any existing M2 tables. Run [m2-team-preflight.sql](../../database/m2-team-preflight.sql) first. The three student foreign keys require an existing indexed `student.username` compatible with `VARCHAR(50)` and `utf8mb4_0900_ai_ci`.
2. Import [m2-schema.sql](../../database/m2-schema.sql) and then [m2-catalog.sql](../../database/m2-catalog.sql) into that database. The schema adds five `m2_*` tables and the seed writes only course and prerequisite rows. Review existing M2 table definitions before applying the migration because `CREATE TABLE IF NOT EXISTS` does not upgrade them.
3. Check `SELECT prerequisite_status, COUNT(*) FROM m2_course GROUP BY prerequisite_status;`. On a fresh import expect 444 rows: 21 structured, 10 with no formal course prerequisites, and 413 requiring review.

The seed derives from [the MIT OCW snapshot](../../datasets/mit-ocw/README.md). Run `node tools/m2/generate-catalog-sql.mjs` from the repository root to regenerate it.

## Run and use the API

Use Java 8 and Maven. For local access to a team database, set `M2_TEAM_DB_URL`, `M2_TEAM_DB_USER`, and `M2_TEAM_DB_PASSWORD` in the backend process and select the `m2team` Spring profile. This profile points the whole Spring application at that data source and listens on loopback. Set `DEEPSEEK_API_KEY` in the backend process only if you intend to enable external recommendations. An unconfigured provider uses the labelled offline fallback. Provider requests contain the student-entered interests and goals plus eligible public course summaries.

The API uses the team's existing student login session. Call `GET /courses/me` to retrieve a CSRF token. Send it in `X-M2-CSRF` for either recommendation POST. The request body accepts `interests`, optional `goals`, and optional integer `topK` from 1 to 3.

| Method and path | Response |
| --- | --- |
| `GET /courses/catalog` | Public course records |
| `GET /courses/me` | Personal profile, completed courses, and CSRF token |
| `GET /courses/history` | Personal recommendation history |
| `POST /courses/recommendations` | Offline recommendations |
| `POST /courses/recommendations/assisted` | Recommendations with the actual mode and fallback reason |

M2 relies on the existing student session and the new token utility. Review the shared account endpoints and authentication before exposing the application to real accounts. This delivery does not change the team's login endpoints.

## Test

From `springboot`, run `mvn "-Dtest=com.example.springboot.m2.*Test" test`. For `MySqlCourseTest` set `M2_MYSQL_TEST=true` and configure `M2_DB_URL`, `M2_DB_USER`, and `M2_DB_PASSWORD` for a disposable MySQL database with the schema and catalogue imported. That test writes temporary student and M2 records. Keep the provider key unset for the deterministic fallback test.
