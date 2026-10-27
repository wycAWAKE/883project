# M5/M6 setup and demonstration

The modules run inside the existing Vue and Spring Boot projects and reuse Student/Staff login. They do not install another login or password store.

## Existing group database

Back up the selected database. Import `database/m5_m6_v1.sql` into that database, then start the framework normally. This adds four sc_ tables and does not modify legacy tables. The SQL contains no USE statement. Select the correct database in the MySQL client.

This v1 migration is repeatable for the group schema. It is not a converter for the earlier independent M5/M6 database, which has different user ID types. Keep that earlier database separate.

## Fresh local setup

Import `database/m5_m6_local_demo.sql` for a NEW isolated database named `smartcampus_group_demo`, then import `database/m5_m6_v1.sql` into it. The first script refuses an existing database. It does not replace the original dormitory database.

Configure the backend datasource environment to match the new database. On the prepared computer, the existing project-local MySQL is reused on port 3307. A second MySQL installation is unnecessary.

The previously missing `dormitory_en_demo.sql` and `test_data_en.sql` are also included for the root README's optional English framework demo. Choose one demo database instead of mixing all initialization files.

## Build and run

Follow the runtime versions in the root README. Backend code retains the Java 8 compilation target and Spring Boot 2.6.3.

In springboot, run `mvn test`, then `mvn -DskipTests package org.springframework.boot:spring-boot-maven-plugin:2.6.3:repackage`. Start the backend with the correct datasource environment.

In vue, run `npm ci`, `npm test`, `npm run build`, then `npm run dev -- --strictPort`.

Open http://localhost:8080/Login. Backend port is 9090. The earlier independent demo on 8090 is a different application.

## Prepared computer: double-click start

Run `start-group.cmd`. The optional helper requires Java 17+, which is already present on this computer. The backend itself retains the group's Java 8 compilation target.

It needs `.local/smartcampus-group.jar`, a built vue/dist, and private `.local/group-runtime.properties`. These are local runtime files and are not committed. The properties are host, port, database, username, password and optional mysqlSource. See `scripts/group-runtime.example.properties`.

The helper hosts the built Vue files on 8080 and forwards /api to 9090, stripping the prefix as Vite does. It binds only to 127.0.0.1. It is a local demo helper, not a replacement for server deployment instructions.

Keep its command window open. Press Ctrl+C before closing. A port-in-use message means another startup window must be checked first. Logs are in .local. Private configuration, generated data, binaries and backups stay out of Git.

## Actual MySQL tests

Normal mvn test runs the framework context test. M5/M6 database tests require explicit opt-in. Without opt-in they are skipped, not passed.

Create the NEW disposable database with `database/m5_m6_test_schema.sql`. It refuses an existing database. Point the datasource environment at `smartcampus_group_m5m6_test` using a test-only database account. Set environment variable SC_GROUP_MYSQL_TEST to true, then run mvn test.

Before clearing module tables, the suite checks SELECT DATABASE() and refuses any other database name. Tests use the real group login endpoints. Never put non-test data in that database.

## Manual demonstration

1. Select Student and sign in as stu1 / 123456.
2. Submit an enrolment certificate request, or a transcript with a supporting file.
3. Sign out, select Staff and sign in as dorm1 / 123456. Return the request with a reason.
4. Sign in as stu1. Read the notification, reason and history. Revise and resubmit.
5. Sign in as Staff and approve the revised request.
6. Sign in as stu1 and verify the result. Sign in as stu2 and confirm the first student's application is not visible.
7. Restart the application and confirm the record and file remain.

Default demo credentials are synthetic. Only Student submits and Staff reviews. Administrator is not automatically an application reviewer.
