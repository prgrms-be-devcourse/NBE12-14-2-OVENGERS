SELECT 'AWS 일반 회원' AS item, COUNT(*) AS count
FROM member m JOIN slotkey_sample_registry r ON r.kind='MEMBER' AND r.row_id=m.id
WHERE r.seed_key REGEXP '^aws-[0-9]{3}$'
UNION ALL
SELECT 'AWS 관리자', COUNT(*) FROM member m
JOIN slotkey_sample_registry r ON r.kind='MEMBER' AND r.row_id=m.id
WHERE r.seed_key='aws-admin-000' OR (r.seed_key='admin-000' AND m.email='admin000@amazon.com')
UNION ALL
SELECT 'AWS 공간', COUNT(*) FROM spaces s
JOIN slotkey_sample_registry r ON r.kind='SPACE' AND r.row_id=s.id
WHERE r.seed_key REGEXP '^aws-(pangyo|hanam|gangnam)-[0-9]{2}$'
UNION ALL
SELECT 'AWS 회원 예약', COUNT(*) FROM reservation q
JOIN slotkey_sample_registry r ON r.kind='MEMBER' AND r.row_id=q.member_id
WHERE r.seed_key REGEXP '^aws-[0-9]{3}$' OR r.seed_key='aws-admin-000';

SELECT s.location, COUNT(*) AS aws_sample_spaces
FROM spaces s JOIN slotkey_sample_registry r ON r.kind='SPACE' AND r.row_id=s.id
WHERE r.seed_key REGEXP '^aws-(pangyo|hanam|gangnam)-[0-9]{2}$'
GROUP BY s.location ORDER BY s.location;
