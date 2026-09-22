SET time_zone = '+09:00';
DELIMITER $$
CREATE PROCEDURE __ROUTINE__()
BEGIN
  DECLARE n INT DEFAULT 1;
  DECLARE sample_id BIGINT;
  DECLARE sample_email VARCHAR(254);
  DECLARE sample_key VARCHAR(100);
  DECLARE sample_nickname VARCHAR(100);
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
  START TRANSACTION;
  WHILE n<=100 DO
    SET sample_email=CONCAT('aws',LPAD(n,3,'0'),'@amazon.com');
    SET sample_key=CONCAT('aws-',LPAD(n,3,'0'));
    SET sample_nickname=CONCAT('AWS',LPAD(n,3,'0'));
    IF NOT EXISTS (SELECT 1 FROM slotkey_sample_registry WHERE kind='MEMBER' AND seed_key=sample_key) THEN
      IF EXISTS (SELECT 1 FROM member WHERE email=sample_email) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Sample email already belongs to an untracked member. No existing data modified.';
      END IF;
      INSERT INTO member(email,password_hash,nickname,role,status,balance,created_at,updated_at)
      VALUES(
  sample_email,
  '$2b$12$kRiVg0sZNHrmMtl9Q7eIguuqTOc0vQY51A/REFpC5sUmr7zyRzM8u',
  sample_nickname,
  'USER',
  'ACTIVE',
  100000,
  NOW(6),
  NOW(6)
);
      SET sample_id=LAST_INSERT_ID();
      INSERT INTO slotkey_sample_registry VALUES('MEMBER',sample_key,sample_id);
      INSERT INTO credit_transaction(member_id,amount,type,reservation_id,balance_after,reason,created_at)
      VALUES(sample_id,100000,'SIGNUP_GRANT',NULL,100000,'AWS 샘플 데이터 초기 잔액',NOW());
    END IF;
    SET n=n+1;
  END WHILE;
  COMMIT;
  DO RELEASE_LOCK(CONCAT(DATABASE(),':slotkey-samples'));
END$$
CALL __ROUTINE__()$$
DELIMITER ;
