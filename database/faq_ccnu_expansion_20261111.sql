-- CCNU source-checked additive FAQ batch, 2026-11-11.
-- Target: localhost:3306/dormitory.faq_entry; existing table required.
-- Scope:35 new natural question/source pairs; existing five rows are preserved.
-- Corpus keys are stable review identifiers, not database primary keys.
-- Single operator only. Stop on first error and roll back the transaction.
-- Source/version audit: ../docs/FAQ_CORPUS_BASELINE.md and corpus JSON.
SET NAMES utf8mb4;
START TRANSACTION;

-- CCNU-FAQ-006
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Who handles undergraduate student-status and qualification enquiries?', 'The Undergraduate School lists 027-67862851 for student-status and academic-qualification enquiries.', 'Academic records', 'student status qualification academic records contact phone', 'CCNU Undergraduate School - Contact us', 'https://jwc.ccnu.edu.cn/ywgl/lxwm.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Who handles undergraduate student-status and qualification enquiries?' AND source_url = 'https://jwc.ccnu.edu.cn/ywgl/lxwm.htm');

-- CCNU-FAQ-007
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Who can help with undergraduate teaching evaluation?', 'The Undergraduate School lists 027-67868058 for student teaching-evaluation enquiries.', 'Teaching evaluation', 'teaching evaluation course feedback student survey contact phone', 'CCNU Undergraduate School - Contact us', 'https://jwc.ccnu.edu.cn/ywgl/lxwm.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Who can help with undergraduate teaching evaluation?' AND source_url = 'https://jwc.ccnu.edu.cn/ywgl/lxwm.htm');

-- CCNU-FAQ-008
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Where is the undergraduate graduate identity-review approval form?', 'The Student Status Management list includes the undergraduate graduate identity-review approval form, dated 19 October 2026.', 'Academic records', 'graduate identity review identity verification approval form', 'CCNU Undergraduate School - Student status management', 'https://jwc.ccnu.edu.cn/xjxw/xjgl.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Where is the undergraduate graduate identity-review approval form?' AND source_url = 'https://jwc.ccnu.edu.cn/xjxw/xjgl.htm');

-- CCNU-FAQ-009
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Where is the application form for a replacement degree certificate statement?', 'The Student Status Management list includes the replacement degree-certification application form, dated 2 September 2026.', 'Academic records', 'degree certificate replacement certification statement lost application form', 'CCNU Undergraduate School - Student status management', 'https://jwc.ccnu.edu.cn/xjxw/xjgl.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Where is the application form for a replacement degree certificate statement?' AND source_url = 'https://jwc.ccnu.edu.cn/xjxw/xjgl.htm');

-- CCNU-FAQ-010
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Where is the application form for a replacement graduation certificate statement?', 'The Student Status Management list includes the replacement graduation-certification application form, dated 2 September 2026.', 'Academic records', 'graduation diploma certificate replacement certification statement lost application form', 'CCNU Undergraduate School - Student status management', 'https://jwc.ccnu.edu.cn/xjxw/xjgl.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Where is the application form for a replacement graduation certificate statement?' AND source_url = 'https://jwc.ccnu.edu.cn/xjxw/xjgl.htm');

-- CCNU-FAQ-011
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Where is the graduate academic-qualification information correction form?', 'The Student Status Management list includes the graduate academic-qualification information correction approval form, dated 2 September 2026.', 'Academic records', 'graduate qualification education record information correction approval form', 'CCNU Undergraduate School - Student status management', 'https://jwc.ccnu.edu.cn/xjxw/xjgl.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Where is the graduate academic-qualification information correction form?' AND source_url = 'https://jwc.ccnu.edu.cn/xjxw/xjgl.htm');

-- CCNU-FAQ-012
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'How can I recover a forgotten unified identity password?', 'The identity guide directs users to Forgot password and verification through registered WeCom, SMS or email channels.', 'Account access', 'unified identity account forgotten password recovery reset authentication', 'CCNU Information Office - Unified identity guide', 'https://nisc.ccnu.edu.cn/info/1115/3204.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'How can I recover a forgotten unified identity password?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1115/3204.htm');

-- CCNU-FAQ-013
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'How can I update the phone number or email registered with unified identity?', 'The published identity guide uses Modify account information in the one-stop portal to update registered contact information.', 'Account access', 'unified identity registered phone mobile email contact information update', 'CCNU Information Office - Unified identity guide', 'https://nisc.ccnu.edu.cn/info/1115/3204.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'How can I update the phone number or email registered with unified identity?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1115/3204.htm');

-- CCNU-FAQ-014
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'How can I change my unified identity password while signed in?', 'The published identity guide uses Modify account password in the one-stop portal account menu.', 'Account access', 'unified identity password change signed in account menu', 'CCNU Information Office - Unified identity guide', 'https://nisc.ccnu.edu.cn/info/1115/3204.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'How can I change my unified identity password while signed in?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1115/3204.htm');

-- CCNU-FAQ-015
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Where can I get in-person help with an unresolved unified identity account problem?', 'The identity guide directs unresolved account issues to the Campus Service Centre in Tianjiabing Building B2, beside ICBC, with identification.', 'Account access', 'unified identity account support in person identification Tianjiabing B2 service centre', 'CCNU Information Office - Unified identity guide', 'https://nisc.ccnu.edu.cn/info/1115/3204.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Where can I get in-person help with an unresolved unified identity account problem?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1115/3204.htm');

-- CCNU-FAQ-016
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Where is the CCNU one-stop service portal?', 'The one-stop platform launch notice gives one.ccnu.edu.cn as the portal address.', 'Digital services', 'one stop service portal digital campus online services website', 'CCNU Information Office - One-stop portal launch notice', 'https://nisc.ccnu.edu.cn/info/1013/4616.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Where is the CCNU one-stop service portal?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1013/4616.htm');

-- CCNU-FAQ-017
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'How can I open the one-stop platform on a phone?', 'The platform launch notice directs mobile users to WeCom, Workbench, then the one-stop platform.', 'Digital services', 'one stop mobile phone WeCom workbench service portal', 'CCNU Information Office - One-stop portal launch notice', 'https://nisc.ccnu.edu.cn/info/1013/4616.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'How can I open the one-stop platform on a phone?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1013/4616.htm');

-- CCNU-FAQ-018
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'What is the information-service support hotline for portal problems?', 'The platform launch notice lists 027-67866000 for information-service enquiries and fault reporting.', 'IT support', 'information service IT helpdesk hotline support portal problems phone', 'CCNU Information Office - One-stop portal launch notice', 'https://nisc.ccnu.edu.cn/info/1013/4616.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'What is the information-service support hotline for portal problems?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1013/4616.htm');

-- CCNU-FAQ-019
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'How should ccnu-auto users sign in again after the February 2026 wireless upgrade?', 'The 2026 notice says to reconnect and authenticate; if no page appears, forget ccnu-auto and reconnect.', 'Campus network', 'ccnu auto wireless wifi upgrade reauthenticate February 2026 reconnect', 'CCNU Information Office - Wireless upgrade reauthentication notice', 'https://nisc.ccnu.edu.cn/info/1013/5829.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'How should ccnu-auto users sign in again after the February 2026 wireless upgrade?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1013/5829.htm');

-- CCNU-FAQ-020
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'How should ccnu Wi-Fi users reauthenticate after the February 2026 upgrade?', 'The 2026 notice says to open a webpage after connecting to ccnu, then sign in on the authentication page.', 'Campus network', 'ccnu wireless wifi upgrade reauthenticate February 2026 browser login', 'CCNU Information Office - Wireless upgrade reauthentication notice', 'https://nisc.ccnu.edu.cn/info/1013/5829.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'How should ccnu Wi-Fi users reauthenticate after the February 2026 upgrade?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1013/5829.htm');

-- CCNU-FAQ-021
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'What can I do if the campus-network authentication page does not appear?', 'The published network guide directs users to portal.ccnu.edu.cn when the authentication page does not open automatically.', 'Campus network', 'campus network authentication login page missing portal browser', 'CCNU Information Office - Campus network guide', 'https://nisc.ccnu.edu.cn/info/1071/3616.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'What can I do if the campus-network authentication page does not appear?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1071/3616.htm');

-- CCNU-FAQ-022
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'What IP setting does the published wired-campus-network guide specify?', 'The wired-network guide specifies automatic IP address acquisition before browser authentication.', 'Campus network', 'wired ethernet campus network automatic IP address DHCP settings', 'CCNU Information Office - Campus network guide', 'https://nisc.ccnu.edu.cn/info/1071/3616.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'What IP setting does the published wired-campus-network guide specify?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1071/3616.htm');

-- CCNU-FAQ-023
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Where can I find campus-network self-service connection details?', 'The guide links network self-service from the authentication page and lists IP, MAC, usage and connected-device information.', 'Campus network', 'campus network self service IP MAC usage devices sessions information', 'CCNU Information Office - Campus network guide', 'https://nisc.ccnu.edu.cn/info/1071/3616.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Where can I find campus-network self-service connection details?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1071/3616.htm');

-- CCNU-FAQ-024
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Where can I report a campus-network fault?', 'The network guide lists 027-67866000 and the Campus Service Centre in Tianjiabing Building B2 for faults.', 'Campus network', 'campus network fault outage repair report Tianjiabing service centre phone', 'CCNU Information Office - Campus network guide', 'https://nisc.ccnu.edu.cn/info/1071/3616.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Where can I report a campus-network fault?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1071/3616.htm');

-- CCNU-FAQ-025
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Who is eligible for the CCNU VPN according to the published guide?', 'The VPN guide covers current students and staff, with free access and no separate activation application.', 'Remote access', 'VPN remote access eligibility current students staff activation', 'CCNU Information Office - VPN operation guide', 'https://nisc.ccnu.edu.cn/info/1095/2500.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Who is eligible for the CCNU VPN according to the published guide?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1095/2500.htm');

-- CCNU-FAQ-026
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Which desktop client and authentication method does the CCNU VPN guide use?', 'The guide directs desktop users to vpn.ccnu.edu.cn for aTrust and requires the Unified identity authentication option.', 'Remote access', 'VPN desktop Windows Mac aTrust client unified identity authentication', 'CCNU Information Office - VPN operation guide', 'https://nisc.ccnu.edu.cn/info/1095/2500.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Which desktop client and authentication method does the CCNU VPN guide use?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1095/2500.htm');

-- CCNU-FAQ-027
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'How does the VPN guide handle an old mobile client after the May 2026 upgrade?', 'The guide says old mobile clients cannot upgrade automatically: reinstall the current aTrust app and use vpn.ccnu.edu.cn.', 'Remote access', 'VPN mobile old client upgrade May 2026 reinstall aTrust phone', 'CCNU Information Office - VPN operation guide', 'https://nisc.ccnu.edu.cn/info/1095/2500.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'How does the VPN guide handle an old mobile client after the May 2026 upgrade?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1095/2500.htm');

-- CCNU-FAQ-028
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'What should I check if CCNU VPN SMS verification fails?', 'The guide says to check account status and the registered mobile number; unresolved problems can use support number 67866000.', 'Remote access', 'VPN SMS verification code missing registered mobile account troubleshooting', 'CCNU Information Office - VPN operation guide', 'https://nisc.ccnu.edu.cn/info/1095/2500.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'What should I check if CCNU VPN SMS verification fails?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1095/2500.htm');

-- CCNU-FAQ-029
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Where is the official campus-card loss and restoration guide?', 'The Information Office campus-card loss/restoration page links its published service-guide PDF.', 'Campus card', 'campus card lost loss restoration unblock service guide', 'CCNU Information Office - Campus-card loss/restoration guide', 'https://nisc.ccnu.edu.cn/info/1057/1081.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Where is the official campus-card loss and restoration guide?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1057/1081.htm');

-- CCNU-FAQ-030
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Where is the official guide for replacing a student or staff campus card?', 'The Information Office card-replacement page links a published in-person service guide; confirm current requirements with the card centre.', 'Campus card', 'student staff campus card replacement lost damaged service guide', 'CCNU Information Office - Student/staff card replacement guide', 'https://nisc.ccnu.edu.cn/info/1057/1054.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Where is the official guide for replacing a student or staff campus card?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1057/1054.htm');

-- CCNU-FAQ-031
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'What does the published campus-card payment PIN reset guide ask me to bring?', 'The guide lists a campus card and identification for the Tianjiabing B2 card centre; confirm current requirements before visiting.', 'Campus card', 'campus card payment consumption PIN password reset identification materials', 'CCNU Information Office - Card payment PIN reset guide', 'https://nisc.ccnu.edu.cn/info/1057/1055.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'What does the published campus-card payment PIN reset guide ask me to bring?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1057/1055.htm');

-- CCNU-FAQ-032
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Where is the university licensed-software service platform?', 'CCNU provides the licensed-software service platform at ms.ccnu.edu.cn, with download and help sections.', 'Software services', 'licensed software genuine Microsoft Office Windows download platform', 'CCNU Licensed Software Management and Service Platform', 'https://ms.ccnu.edu.cn/', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Where is the university licensed-software service platform?' AND source_url = 'https://ms.ccnu.edu.cn/');

-- CCNU-FAQ-033
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Where is the temporary campus internet account application guide?', 'The Information Office publishes a temporary-account guide describing an application by the requesting unit and departmental approval.', 'Guest access', 'temporary guest visitor internet network account application requesting unit', 'CCNU Information Office - Temporary internet account application guide', 'https://nisc.ccnu.edu.cn/info/1055/1082.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Where is the temporary campus internet account application guide?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1055/1082.htm');

-- CCNU-FAQ-034
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'How does the published CCNU WeCom guide begin sign-in?', 'The guide begins with mobile-number sign-in using the phone number registered with the university.', 'Digital services', 'WeCom enterprise WeChat sign in registered mobile phone guide', 'CCNU Information Office - WeCom usage guide', 'https://nisc.ccnu.edu.cn/info/1115/3208.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'How does the published CCNU WeCom guide begin sign-in?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1115/3208.htm');

-- CCNU-FAQ-035
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Where can I find help when CCNU membership is missing from WeCom?', 'Use the published WeCom account-reset guide; it warns that resetting removes the old account chat and file history.', 'Digital services', 'WeCom enterprise WeChat missing university organization membership account reset', 'CCNU Information Office - WeCom account reset service', 'https://nisc.ccnu.edu.cn/info/1115/3198.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Where can I find help when CCNU membership is missing from WeCom?' AND source_url = 'https://nisc.ccnu.edu.cn/info/1115/3198.htm');

-- CCNU-FAQ-036
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'What are the routine CCNU Museum opening hours?', 'Routine hours are 09:00-11:30 and 14:00-17:00 daily; last admission is 16:30. Check special notices.', 'Museum visits', 'museum opening hours visiting time afternoon morning last admission', 'CCNU Museum - Visitor information', 'https://mus.ccnu.edu.cn/index.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'What are the routine CCNU Museum opening hours?' AND source_url = 'https://mus.ccnu.edu.cn/index.htm');

-- CCNU-FAQ-037
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'Is admission to the CCNU Museum free?', 'The museum visitor guide states that admission is free.', 'Museum visits', 'museum admission entrance ticket free cost fee', 'CCNU Museum - Visitor information', 'https://mus.ccnu.edu.cn/index.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'Is admission to the CCNU Museum free?' AND source_url = 'https://mus.ccnu.edu.cn/index.htm');

-- CCNU-FAQ-038
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'How should visitors reserve a CCNU Museum visit?', 'Book through the museum WeChat account; groups of ten or more need two days advance booking.', 'Museum visits', 'museum visit reservation booking group WeChat advance appointment', 'CCNU Museum - Visitor information', 'https://mus.ccnu.edu.cn/index.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'How should visitors reserve a CCNU Museum visit?' AND source_url = 'https://mus.ccnu.edu.cn/index.htm');

-- CCNU-FAQ-039
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'What identification do CCNU students and staff use to enter the museum?', 'The visitor guide tells university students and staff to use their campus card for entry.', 'Museum visits', 'museum campus students staff entry identification card', 'CCNU Museum - Visitor information', 'https://mus.ccnu.edu.cn/index.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'What identification do CCNU students and staff use to enter the museum?' AND source_url = 'https://mus.ccnu.edu.cn/index.htm');

-- CCNU-FAQ-040
INSERT INTO dormitory.faq_entry
    (question, answer, category, keywords, source_title, source_url, enabled)
SELECT 'What identification do external visitors use to enter the CCNU Museum?', 'The visitor guide tells external individuals and groups to use an identity card for entry.', 'Museum visits', 'museum external public visitors group identity identification card', 'CCNU Museum - Visitor information', 'https://mus.ccnu.edu.cn/index.htm', 1
WHERE NOT EXISTS (SELECT 1 FROM dormitory.faq_entry WHERE question = 'What identification do external visitors use to enter the CCNU Museum?' AND source_url = 'https://mus.ccnu.edu.cn/index.htm');

COMMIT;
