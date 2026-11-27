# M5/M6 validation record

Date: 26 October 2026. Base: 883project main commit 1d03329. These results cover this M5/M6 branch, not unfinished M1–M4 business features.

## Environment and results

- Windows, Temurin JDK 25.0.4.1. Maven 3.9.9 compiled backend classes for Java 8. Spring Boot and MyBatis-Plus remain 2.6.3 and 3.5.1.
- MySQL 8.4.13. Automated tests used only smartcampus_group_m5m6_test. Browser demonstration used the separate smartcampus_group_demo.
- Backend: 16 module integration tests and 1 existing framework context test passed, 0 failures, 0 errors, 0 skipped. The module suite was explicitly enabled for actual MySQL.
- Frontend: 20 existing tests passed. The route regression test now distinguishes implemented M5/M6 pages from M1–M4 placeholders.
- Frontend production build and executable Spring Boot packaging succeeded. Dependency annotation/size warnings remain from the existing frontend packages.

The module tests cover the real group login, full return/resubmit/approve workflow, student/staff permissions, forged owner fields, unauthenticated access and form token rejection, sign-out, explicit administrator policy, canonical account names, repeated submission, required review reasons, terminal states, invalid fields and pagination, required material, transactional rollback, file validation/ownership, historical material retention, read-state ownership and two simultaneous reviewers.

## Browser checks

Used the real Vue production build through the local same-origin proxy on 8080 and the group backend on 9090.

1. Student stu1 signed in through the existing login page and submitted a Transcript request with supporting-material.png.
2. Staff dorm1 opened it and returned it with a specific reason.
3. Student read the notice, opened the linked application, revised the purpose and resubmitted.
4. Staff approved revision 2. Detail showed four events: submitted, returned, resubmitted and approved.
5. Student saw the approval and could download the material. Download SHA-256 matched the original: 9da80a552636afad72f20c1d092d03074be08f7cd6c475f524cb909a6419b1f9.
6. Student stu2 saw no applications belonging to stu1.
7. Mark-all-read produced zero unread notices. Unread-only filter displayed the empty state.
8. A backend/frontend stop and restart required login again and preserved the application, four history events, four notices and material.
9. At 390 x 844, checked the notification toolbar/empty state and application form. Restored the default desktop viewport afterward.

The browser's native review confirmation caused an automation issue. It was replaced with an in-page confirmation showing the decision and reference. The revised approval flow passed.

Screenshots in m5-m6-evidence use synthetic accounts and records. They are actual application screenshots.

## Backup and restore

Dumped smartcampus_group_demo using mysqldump with a transaction-consistent snapshot. Restored into a separately named validation database. Compared all columns, including the material bytes, for student, dorm_manager and all four module tables. Source and restored values matched. Business rows at that point: 1 application, 1 material, 4 history events and 4 notices. Backups and private credentials remain outside version control.

## Limits

This verifies local integration into the downloaded group framework. It does not establish completion of M1–M4, other members' later changes, a shared server deployment or the entire legacy backend's security. Account keys and the administrator policy are documented in M5_M6_INTEGRATION.md. Cross-module notifications require an agreed contract.

Java 8 is the compiler target. This run used JDK 25, not a separate JDK 8 runtime. Optional double-click helpers require Java 17+. Normal development follows the root README.
