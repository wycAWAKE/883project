-- SmartCampus FAQ: CCNU source-checked initial batch, prepared 2026-10-26.
-- Deployed to dormitory on 2026-10-26 under explicit user authorization; see FAQ_DATA_REVIEW.md.
-- Re-running this script still requires authorization for the intended target database.
-- Target: localhost:3306, schema dormitory, table faq_entry (InnoDB).
-- Prerequisite: separately review and execute database/faq.sql against dormitory.
-- Source audit and operator checklist: ../springboot/src/main/java/com/example/springboot/faq/FAQ_DATA_REVIEW.md
-- Every reference is a public CCNU official page. English answers are project paraphrases.
-- No claim of CCNU approval or certified English translation is made.
-- Fully qualified table names prevent the current default schema changing the target.
-- Repeat execution skips the same question + source URL and never updates existing rows.
-- Run once with a single operator; no concurrency guarantee is provided without a unique key.
-- Use a client that stops on the first SQL error; ROLLBACK instead of COMMIT on failure.
-- No schema changes, account records, deletes or replacement of existing FAQ content.

SET NAMES utf8mb4;
START TRANSACTION;

-- CCNU-FAQ-001. Official calendar page, published 2026-05-21.
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT
    'Where can I find the CCNU academic calendar for 2026-2027?',
    'The CCNU Undergraduate School website publishes the academic calendar for 2026-2027. Open the linked official calendar page to view it.',
    'Academic calendar',
    'calendar term semester academic year timetable 2026 2027',
    'CCNU Undergraduate School - Academic calendar for 2026-2027',
    'https://jwc.ccnu.edu.cn/info/1132/28511.htm',
    1
WHERE NOT EXISTS (
    SELECT 1 FROM dormitory.faq_entry
    WHERE question = 'Where can I find the CCNU academic calendar for 2026-2027?'
      AND source_url = 'https://jwc.ccnu.edu.cn/info/1132/28511.htm'
);

-- CCNU-FAQ-002. Template page dated 2015-12-09, still listed in Student Downloads.
-- This answer locates the published template; it does not certify that it is the latest version.
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT
    'Where can I download an English undergraduate transcript template?',
    'The Undergraduate School page for undergraduate transcript templates provides an English transcript template as an attachment. This is a template, not an issued transcript.',
    'Academic records',
    'transcript transcripts english template download grades record undergraduate',
    'CCNU Undergraduate School - Undergraduate transcript templates',
    'https://jwc.ccnu.edu.cn/info/1115/3640.htm',
    1
WHERE NOT EXISTS (
    SELECT 1 FROM dormitory.faq_entry
    WHERE question = 'Where can I download an English undergraduate transcript template?'
      AND source_url = 'https://jwc.ccnu.edu.cn/info/1115/3640.htm'
);

-- CCNU-FAQ-003. Only the listing and its 2026-09-02 entry were checked, not the form attachment.
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT
    'Where can I find the student registration information change form?',
    'The Undergraduate School Student Status Management page lists the student registration information change application form. Open the form entry on that page.',
    'Student registration',
    'registration information change correction status details application form',
    'CCNU Undergraduate School - Student status management',
    'https://jwc.ccnu.edu.cn/xjxw/xjgl.htm',
    1
WHERE NOT EXISTS (
    SELECT 1 FROM dormitory.faq_entry
    WHERE question = 'Where can I find the student registration information change form?'
      AND source_url = 'https://jwc.ccnu.edu.cn/xjxw/xjgl.htm'
);

-- CCNU-FAQ-004. Public course-selection enquiry number on the official contact page.
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT
    'Who can I contact about undergraduate course selection?',
    'For undergraduate course-selection enquiries, the Undergraduate School lists 027-67863133. Check the source page for current contact details.',
    'Course selection',
    'course selection enrolment enrollment choosing classes consultation phone contact',
    'CCNU Undergraduate School - Contact us (course-selection enquiries)',
    'https://jwc.ccnu.edu.cn/ywgl/lxwm.htm',
    1
WHERE NOT EXISTS (
    SELECT 1 FROM dormitory.faq_entry
    WHERE question = 'Who can I contact about undergraduate course selection?'
      AND source_url = 'https://jwc.ccnu.edu.cn/ywgl/lxwm.htm'
);

-- CCNU-FAQ-005. Public Student Affairs Service Hall number on the same official contact page.
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT
    'How can I contact the Undergraduate School Student Affairs Service Hall?',
    'The Undergraduate School contact page lists 027-67865223 for the Student Affairs Service Hall. Use that number for enquiries about its services.',
    'Student services',
    'student affairs service hall desk support help office phone contact',
    'CCNU Undergraduate School - Contact us (Student Affairs Service Hall)',
    'https://jwc.ccnu.edu.cn/ywgl/lxwm.htm',
    1
WHERE NOT EXISTS (
    SELECT 1 FROM dormitory.faq_entry
    WHERE question = 'How can I contact the Undergraduate School Student Affairs Service Hall?'
      AND source_url = 'https://jwc.ccnu.edu.cn/ywgl/lxwm.htm'
);

COMMIT;
