-- M2 team integration preflight: SELECT only; no data or schema changes.
-- Select the intended database in your MySQL client before running this file.
-- Results contain schema metadata, not student records or passwords.
SELECT DATABASE() AS selected_database, VERSION() AS mysql_version,
       @@character_set_database AS database_charset,
       @@collation_database AS database_collation;

-- M2 currently requires student.username to be a compatible unique identifier.
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE,
       CHARACTER_SET_NAME, COLLATION_NAME, COLUMN_KEY
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'student' AND COLUMN_NAME = 'username';

-- A composite index is not automatically sufficient: inspect the full key.
SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'student'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- Existing names must be inspected; IF NOT EXISTS does not repair old schemas.
SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('m2_course','m2_prerequisite','m2_completed','m2_profile','m2_history')
ORDER BY TABLE_NAME;

SELECT TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE,
       IS_NULLABLE, COLUMN_DEFAULT, CHARACTER_SET_NAME, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('m2_course','m2_prerequisite','m2_completed','m2_profile','m2_history')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

SELECT TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME,
       REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('m2_course','m2_prerequisite','m2_completed','m2_profile','m2_history')
ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION;
