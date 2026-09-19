DELIMITER $$
CREATE PROCEDURE __ROUTINE__()
BEGIN
  DECLARE obtained INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    IF obtained=1 THEN DO RELEASE_LOCK(CONCAT(DATABASE(),':slotkey-samples')); END IF;
    RESIGNAL;
  END;
  SELECT GET_LOCK(CONCAT(DATABASE(),':slotkey-samples'),10) INTO obtained;
  IF obtained IS NULL OR obtained<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Another sample setup is running.';
  END IF;
  CREATE TEMPORARY TABLE sample_owned_reservations(id BIGINT PRIMARY KEY);
  START TRANSACTION;
  -- 부모 행을 잠가 FK를 추가하는 동시 예약/토큰 요청과 충돌하지 않도록 합니다.
  UPDATE member SET id=id WHERE id IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='MEMBER');
  UPDATE spaces SET id=id WHERE id IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='SPACE');
  INSERT INTO sample_owned_reservations SELECT id FROM reservation
    WHERE member_id IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='MEMBER') FOR UPDATE;

  -- 일반 회원의 예약을 지우거나 일반 회원의 잔액/원장을 깨뜨리지 않습니다.
  IF EXISTS (
    SELECT 1 FROM reservation WHERE space_id IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='SPACE')
    AND id NOT IN (SELECT id FROM sample_owned_reservations)
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Non-sample users reserved sample spaces. Cleanup refused; no data deleted.';
  END IF;
  IF EXISTS (
    SELECT 1 FROM credit_transaction WHERE reservation_id IN (SELECT id FROM sample_owned_reservations)
    AND member_id NOT IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='MEMBER')
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Non-sample credit ledger references sample bookings. Cleanup refused.';
  END IF;
  IF EXISTS (
    SELECT 1 FROM reservation_status_history
    WHERE changed_by_member_id IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='MEMBER')
    AND reservation_id NOT IN (SELECT id FROM sample_owned_reservations)
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Sample member changed a non-sample booking. Cleanup refused.';
  END IF;
  IF EXISTS (
    SELECT 1 FROM door_access_log
    WHERE (actor_member_id IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='MEMBER')
      OR requested_space_id IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='SPACE'))
    AND reservation_id IS NOT NULL AND reservation_id NOT IN (SELECT id FROM sample_owned_reservations)
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Access logs link sample data to a non-sample booking. Cleanup refused.';
  END IF;
  IF EXISTS (
    SELECT 1 FROM reservation_slot WHERE space_id IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='SPACE')
    AND reservation_id NOT IN (SELECT id FROM sample_owned_reservations)
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Non-sample slots reference sample spaces. Cleanup refused.';
  END IF;

  DELETE FROM door_access_log WHERE reservation_id IN (SELECT id FROM sample_owned_reservations)
    OR actor_member_id IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='MEMBER')
    OR requested_space_id IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='SPACE');
  DELETE FROM door_access_token WHERE reservation_id IN (SELECT id FROM sample_owned_reservations);
  DELETE FROM reservation_status_history WHERE reservation_id IN (SELECT id FROM sample_owned_reservations);
  DELETE FROM reservation_slot WHERE reservation_id IN (SELECT id FROM sample_owned_reservations);
  DELETE FROM credit_transaction WHERE member_id IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='MEMBER');
  DELETE FROM idempotency_key WHERE member_id IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='MEMBER');
  DELETE FROM refresh_token WHERE member_id IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='MEMBER');
  -- 대상이 샘플인 감사 기록만 삭제. 일반 공간 등에 남긴 감사 기록은 보존합니다.
  DELETE FROM audit_logs WHERE
    (target_type='RESERVATION' AND target_id IN (SELECT id FROM sample_owned_reservations)) OR
    (target_type='MEMBER' AND target_id IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='MEMBER')) OR
    (target_type='SPACE' AND target_id IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='SPACE'));
  DELETE FROM reservation WHERE id IN (SELECT id FROM sample_owned_reservations);
  DELETE FROM spaces WHERE id IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='SPACE');
  DELETE FROM member WHERE id IN (SELECT row_id FROM slotkey_sample_registry WHERE kind='MEMBER');
  DELETE FROM slotkey_sample_registry WHERE kind IN ('MEMBER','SPACE');
  COMMIT;
  DO RELEASE_LOCK(CONCAT(DATABASE(),':slotkey-samples'));
END$$
CALL __ROUTINE__()$$
DELIMITER ;
