SET time_zone = '+09:00';
SET SESSION sql_mode = 'STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION';
DELIMITER $$
CREATE PROCEDURE __ROUTINE__()
BEGIN
  DECLARE n INT DEFAULT 1;
  DECLARE region_idx INT DEFAULT 1;
  DECLARE sample_id BIGINT;
  DECLARE sample_email VARCHAR(254);
  DECLARE sample_key VARCHAR(100);
  DECLARE sample_nickname VARCHAR(100);
  DECLARE sample_name VARCHAR(100);
  DECLARE region_name VARCHAR(20);
  DECLARE region_slug VARCHAR(20);
  DECLARE photo_idx INT;
  DECLARE sample_image VARCHAR(500);
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
  IF EXISTS (SELECT 1 FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name IN
    ('member','spaces','credit_transaction','slotkey_sample_registry') AND engine<>'InnoDB') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Sample tables must use InnoDB for transactional setup.';
  END IF;
  START TRANSACTION;
  IF @aws_sample_create_missing IS NULL OR @aws_sample_create_missing NOT IN (0,1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Run through aws-setup.sh with setup or update.';
  END IF;
  IF @aws_sample_create_missing=1 AND (
    @aws_sample_password_hash IS NULL OR
    NOT REGEXP_LIKE(@aws_sample_password_hash,'^[$]2[aby][$][0-9]{2}[$][./A-Za-z0-9]{53}$','c')
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A BCrypt hash is required for new sample accounts.';
  END IF;
  -- 이전 AWS SQL의 공통 키를 AWS 전용 키로 이동합니다. 로컬 관리자는 유지합니다.
  IF EXISTS (SELECT 1 FROM slotkey_sample_registry r JOIN member m ON m.id=r.row_id
    WHERE r.kind='MEMBER' AND r.seed_key='admin-000' AND m.email='admin000@amazon.com') THEN
    IF EXISTS (SELECT 1 FROM slotkey_sample_registry WHERE kind='MEMBER' AND seed_key='aws-admin-000') THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Both legacy and AWS admin keys exist. Review registry first.';
    END IF;
    UPDATE slotkey_sample_registry SET seed_key='aws-admin-000'
    WHERE kind='MEMBER' AND seed_key='admin-000';
  END IF;
  IF EXISTS (SELECT 1 FROM slotkey_sample_registry r LEFT JOIN member m ON m.id=r.row_id
    WHERE r.kind='MEMBER' AND (r.seed_key REGEXP '^aws-[0-9]{3}$' OR r.seed_key='aws-admin-000')
    AND m.id IS NULL) OR EXISTS (
    SELECT 1 FROM slotkey_sample_registry r LEFT JOIN spaces s ON s.id=r.row_id
    WHERE r.kind='SPACE' AND r.seed_key REGEXP '^aws-(pangyo|hanam|gangnam)-[0-9]{2}$'
    AND s.id IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='AWS registry contains missing rows. Review registry first.';
  END IF;
  WHILE n<=100 DO
    SET sample_email=CONCAT('aws',LPAD(n,3,'0'),'@amazon.com');
    SET sample_key=CONCAT('aws-',LPAD(n,3,'0'));
    SET sample_nickname=CONCAT('AWS',LPAD(n,3,'0'));
    IF EXISTS (SELECT 1 FROM slotkey_sample_registry r JOIN member m ON m.id=r.row_id
      WHERE r.kind='MEMBER' AND r.seed_key=sample_key AND m.email<>sample_email) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Registered AWS member email changed. No account overwritten.';
    END IF;
    IF @aws_sample_create_missing=1 AND NOT EXISTS (SELECT 1 FROM slotkey_sample_registry WHERE kind='MEMBER' AND seed_key=sample_key) THEN
      IF EXISTS (SELECT 1 FROM member WHERE email=sample_email) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Sample email already belongs to an untracked member. No existing data modified.';
      END IF;
      INSERT INTO member(email,password_hash,nickname,role,status,balance,created_at,updated_at)
      VALUES(sample_email,@aws_sample_password_hash,sample_nickname,
        'USER','ACTIVE',100000,NOW(6),NOW(6));
      SET sample_id=LAST_INSERT_ID();
      INSERT INTO slotkey_sample_registry VALUES('MEMBER',sample_key,sample_id);
      INSERT INTO credit_transaction(member_id,amount,type,reservation_id,balance_after,reason,created_at)
      VALUES(sample_id,100000,'SIGNUP_GRANT',NULL,100000,'AWS 샘플 데이터 초기 잔액',NOW());
    END IF;
    SET n=n+1;
  END WHILE;
  WHILE region_idx<=3 DO
    SET region_name=ELT(region_idx,'판교','하남','강남');
    SET region_slug=ELT(region_idx,'pangyo','hanam','gangnam');
    SET n=1;
    WHILE n<=13 DO
      SET sample_key=CONCAT('aws-',region_slug,'-',LPAD(n,2,'0'));
      SET photo_idx=1+MOD((region_idx-1)*13+n-1,39);
      SET sample_name=CONCAT('[AWS] ',region_name,' ',ELT(photo_idx,'차콜 포커스룸','골든 포레스트 컨퍼런스룸','스노우뷰 미팅룸','코랄 미팅룸','클래스 세미나룸','파크뷰 워크숍룸','선샤인 오피스','우드 커넥트룸','쉐도우 라운지','화이트보드 미팅룸','포커스 클래스룸','미드나잇 블루룸','오픈 클래스룸','모노 미팅룸','그랜드 세미나홀','갤러리 컨퍼런스룸','글라스 오피스','우드 데스크룸','브릭 우드 미팅룸','라이트 미팅룸','베리 브릭룸','오렌지 포인트룸','스카이라이트 보드룸','그린 테이블룸','시티뷰 미팅룸','노트북 워크룸','그리너리 워크라운지','팀 커넥트룸','쿨그레이 미팅룸','우드 스퀘어룸','오크 워크숍룸','펜던트 라운지','블루 가든룸','블랙 체어 클래스룸','플랜트 스크린룸','플랜트 스튜디오룸','블루 오디토리움','데이라이트 세미나룸','민트 글라스룸'));
      SET sample_image=CONCAT('/api/v1/space-images/a5100000-0000-4000-8000-',LPAD(photo_idx,12,'0'),'.jpg');
      IF @aws_sample_create_missing=1 AND NOT EXISTS (SELECT 1 FROM slotkey_sample_registry WHERE kind='SPACE' AND seed_key=sample_key) THEN
        IF EXISTS (SELECT 1 FROM spaces WHERE name=sample_name) THEN
          SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Sample space name belongs to untracked data. No existing data modified.';
        END IF;
        INSERT INTO spaces(name,location,description,capacity,price_per_slot,image_path,opening_time,closing_time,status,version)
        VALUES(sample_name,region_name,CONCAT(region_name,' 예약 기능 검증용 AWS 샘플 공간입니다. 이미지와 주소는 샘플입니다.'),
          ELT(1+MOD(n-1,5),2,4,6,8,12),2000+MOD(n-1,10)*500,
          sample_image,'08:00:00','23:00:00','ACTIVE',0);
        INSERT INTO slotkey_sample_registry VALUES('SPACE',sample_key,LAST_INSERT_ID());
      END IF;
      -- 표시 변경은 가격 version을 올리지 않습니다. 기존 가격/예약/회원 상태를 보존합니다.
      UPDATE spaces s
      JOIN slotkey_sample_registry r ON r.kind='SPACE' AND r.seed_key=sample_key AND r.row_id=s.id
      SET s.name=sample_name, s.image_path=sample_image
      WHERE NOT (s.name <=> sample_name) OR NOT (s.image_path <=> sample_image);
      SET n=n+1;
    END WHILE;
    SET region_idx=region_idx+1;
  END WHILE;
  IF EXISTS (SELECT 1 FROM slotkey_sample_registry r JOIN member m ON m.id=r.row_id
    WHERE r.kind='MEMBER' AND r.seed_key='aws-admin-000' AND m.email<>'admin000@amazon.com') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Registered AWS admin email changed. No account overwritten.';
  END IF;
  -- 샘플 관리자는 지역 루프 밖에서 한 번만 생성합니다.
  IF @aws_sample_create_missing=1 AND NOT EXISTS (
    SELECT 1 FROM slotkey_sample_registry WHERE kind='MEMBER' AND seed_key='aws-admin-000'
  ) THEN
    IF EXISTS (SELECT 1 FROM member WHERE email='admin000@amazon.com') THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Admin email already belongs to an untracked member.';
    END IF;
    INSERT INTO member(email,password_hash,nickname,role,status,balance,created_at,updated_at)
    VALUES('admin000@amazon.com',@aws_sample_password_hash,'AWS관리자','ADMIN','ACTIVE',0,NOW(6),NOW(6));
    SET sample_id=LAST_INSERT_ID();
    INSERT INTO slotkey_sample_registry VALUES('MEMBER','aws-admin-000',sample_id);
  END IF;
  COMMIT;
  DO RELEASE_LOCK(CONCAT(DATABASE(),':slotkey-samples'));
END$$
CALL __ROUTINE__()$$
DELIMITER ;
