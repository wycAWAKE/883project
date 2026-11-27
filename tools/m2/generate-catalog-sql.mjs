import { readFileSync, writeFileSync } from 'node:fs'
const root = new URL('../../', import.meta.url)
const data = JSON.parse(readFileSync(new URL('datasets/mit-ocw/catalog.json', root), 'utf8'))
const q = value => "'" + String(value).replaceAll("'", "''") + "'"
const lines = [
  '-- Generated from datasets/mit-ocw/catalog.json; MIT OCW / named instructors, CC BY-NC-SA 4.0.',
  '-- Historical metadata normalized for a noncommercial educational prototype. Not current GCU/MIT enrollment data.',
  'SET NAMES utf8mb4;',
  'SET @m2_saved_sql_mode = @@SESSION.sql_mode;',
  "SET SESSION sql_mode = CONCAT(@@SESSION.sql_mode, ',NO_BACKSLASH_ESCAPES');",
  'START TRANSACTION;',
]
for (const c of data.courses) {
  const values = [c.code,c.sourceCourseCode,c.title,c.description,c.topics.join(' '),c.year,c.sourceUrl,c.syllabusUrl,c.sourceSha256,c.licenseUrl,JSON.stringify(c.instructors),c.prerequisiteStatus,c.prerequisiteEvidence]
  const columns = ['code','source_course_code','title','description','topics','source_year','source_url','syllabus_url','source_sha256','license_url','instructors_json','prerequisite_status','prerequisite_evidence']
  lines.push(`INSERT INTO m2_course (${columns.join(',')}) VALUES (${values.map(q).join(',')}) ON DUPLICATE KEY UPDATE ${columns.slice(1).map(x=>`${x}=VALUES(${x})`).join(',')};`)
  lines.push(`DELETE FROM m2_prerequisite WHERE course_code=${q(c.code)};`)
  c.prerequisiteGroups.forEach((group, i) => group.forEach(code => lines.push(`INSERT INTO m2_prerequisite (course_code,group_no,prerequisite_code) VALUES (${q(c.code)},${i},${q(code)});`)))
}
lines.push('COMMIT;', 'SET SESSION sql_mode = @m2_saved_sql_mode;')
writeFileSync(new URL('database/m2-catalog.sql', root), lines.join('\n')+'\n')
console.log(`Generated ${data.courses.length} attributed courses; private history/completions retained.`)
