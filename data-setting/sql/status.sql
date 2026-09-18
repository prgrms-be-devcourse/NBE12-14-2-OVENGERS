SELECT '샘플 회원' AS item, COUNT(*) AS count
FROM member m JOIN slotkey_sample_registry r ON r.kind='MEMBER' AND r.row_id=m.id
UNION ALL
SELECT '샘플 공간', COUNT(*) FROM spaces s JOIN slotkey_sample_registry r ON r.kind='SPACE' AND r.row_id=s.id
UNION ALL
SELECT '샘플 회원 예약', COUNT(*) FROM reservation q JOIN slotkey_sample_registry r ON r.kind='MEMBER' AND r.row_id=q.member_id;
SELECT s.location, COUNT(*) AS sample_spaces
FROM spaces s JOIN slotkey_sample_registry r ON r.kind='SPACE' AND r.row_id=s.id
GROUP BY s.location ORDER BY s.location;
