# M5/M6 integration contract

## Scope and identity

M5 owns document applications, materials, staff review and revision. M6 displays their status, history and application-linked notices. M1–M4 are unchanged. Other module notices require a later event agreement with their owners.

The module reads the existing server-side Identity and User Session attributes. Student maps to stu, Staff to dormManager. It re-reads the canonical username, name and email from the existing account table. Ownership keys are stu:<username> and dormManager:<username>, avoiding role-table collisions. It does not copy passwords or create another account store.

The legacy tables have no shared numeric ID or programme field. The student account username is displayed as the account identifier. Programme remains empty rather than invented. Account renaming/deletion needs a separate group policy because business records keep stable keys.

Student can submit, revise a returned application, view own records and download own materials. Staff can review and download materials bound to an application. Administrator does not implicitly become a reviewer. Every new endpoint checks the Session. Writes additionally check a session-bound form token that changes with the signed-in identity. Legacy account endpoints and global authentication hardening are outside this module's scope.

## HTTP contract

Use the existing request.js with baseURL /api. Backend paths do not contain /api because the proxy strips that prefix. Success follows the framework shape {code,msg,data}, with string code "0". Errors use matching HTTP statuses and string codes. Product messages are English.

| Method | Backend path | Purpose |
| --- | --- | --- |
| GET | /applications/session | Current module identity and csrfToken |
| GET | /applications/summary | Counts within authorized scope |
| GET | /applications | Status/search/pageNum/pageSize, maximum size 50 |
| GET | /applications/{id} | Application, files and history |
| POST | /applications | Student submits |
| PUT | /applications/{id}/resubmit | Revise a returned application |
| POST | /applications/{id}/decision | Staff review |
| POST | /attachments | Multipart file |
| GET | /attachments/pending | Own unsubmitted materials |
| DELETE | /attachments/{id} | Delete own unsubmitted material |
| GET | /attachments/{id}/download | Authorized download |
| GET | /notifications | Own notices and optional unread filter |
| GET | /notifications/unread-count | Own unread count |
| PUT | /notifications/{id}/read | Mark own notice read, repeatable |
| PUT | /notifications/read-all | Mark own notices read |

Writes send X-CSRF-Token from the module Session endpoint and retain the existing Cookie. Client-supplied user IDs and role fields never establish identity.

Submission fields: requestKey (UUID), applicationType, purpose (5–1000 trimmed characters), documentLanguage, deliveryMethod, optional notes, attachmentIds (up to 5). Resubmission also sends the current version. Types are ENROLMENT_CERTIFICATE and TRANSCRIPT. Languages are ENGLISH and CHINESE. Delivery methods are ELECTRONIC and PICKUP.

Review sends action (RETURN, APPROVE or REJECT), reason and version. Returning/rejecting requires a reason. States are PENDING, RETURNED, APPROVED and REJECTED. Only returned applications can be revised. Final outcomes cannot be reviewed again. Compare-and-update on status and version rejects competing/stale reviews. A request key safely retries the same submission.

## Data and materials

Four added tables: sc_application, sc_attachment, sc_history, sc_notification. State, file binding, revision, history and notices share a database transaction. A failed operation rolls back the entire change.

Files must match PDF/PNG/JPEG signatures and extensions, with a 5 MB maximum. Bytes live in MySQL MEDIUMBLOB and are included in a database backup. Each student can retain up to 20 unsubmitted materials, attach them later or delete unused files. Submitted materials cannot be deleted through the temporary upload endpoint. Older versions keep the original files and snapshots.

Back up before schema changes. m5_m6_v1.sql adds module tables without selecting a database or changing old tables. It is not compatible with the earlier standalone module's numeric user IDs. Use a separate database for this group version.
