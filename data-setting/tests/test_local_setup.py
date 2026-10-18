"""로컬 샘플 SQL(setup.sql, clean.sql) 검증. 개발 DB와 분리한 임시 MySQL 컨테이너에서만 실행한다.

python3 data-setting/tests/test_local_setup.py --mysql-container slotkey-aws-seed-check
"""
import argparse
from pathlib import Path
import re
import subprocess
import sys
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[1]
HASH = "$2b$12$kRiVg0sZNHrmMtl9Q7eIguuqTOc0vQY51A/REFpC5sUmr7zyRzM8u"
CONTAINER = None


@unittest.skipUnless(CONTAINER, "--mysql-container로 별도 검증 컨테이너를 지정하세요")
class LocalSetupTest(unittest.TestCase):
    def setUp(self):
        self.database = "slotkey_local_test_" + uuid.uuid4().hex[:12]
        self.sql(f"CREATE DATABASE `{self.database}` CHARACTER SET utf8mb4;", use_database=False)
        self.addCleanup(lambda: self.sql(f"DROP DATABASE `{self.database}`;", use_database=False))
        migrations = ROOT.parent / "backend/src/main/resources/db/migration"
        files = sorted(migrations.glob("V*.sql"), key=lambda p: int(re.match(r"V(\d+)", p.name)[1]))
        self.sql("\n".join(p.read_text() for p in files))
        self.sql("""CREATE TABLE slotkey_sample_registry (
kind VARCHAR(20) NOT NULL, seed_key VARCHAR(100) NOT NULL, row_id BIGINT NOT NULL,
PRIMARY KEY(kind,seed_key), UNIQUE KEY uq_sample_row(kind,row_id)) ENGINE=InnoDB;""")

    def sql(self, source, use_database=True, succeeds=True):
        command = ["docker", "exec", "-i", CONTAINER, "mysql", "--user=root",
                   "--default-character-set=utf8mb4", "--batch", "--skip-column-names"]
        if use_database:
            command.append("--database=" + self.database)
        result = subprocess.run(command, input=source, capture_output=True, text=True)
        self.assertEqual(result.returncode == 0, succeeds, result.stderr)
        return result.stdout.strip()

    def run_routine(self, operation, succeeds=True):
        routine = f"local_{operation}_test_" + uuid.uuid4().hex[:12]
        source = (ROOT / f"sql/{operation}.sql").read_text().replace("__ROUTINE__", routine)
        try:
            return self.sql(source, succeeds=succeeds)
        finally:
            self.sql(f"DROP PROCEDURE IF EXISTS `{routine}`;")

    def test_setup_stores_credentials_and_region_names(self):
        self.run_routine("setup")
        self.run_routine("setup")
        self.assertEqual(self.sql("SELECT COUNT(*) FROM member; SELECT COUNT(*) FROM spaces;"), "101\n39")
        # V14 이후 비밀번호 해시는 local_credential에 있다. V15 정책처럼 샘플 회원은 이메일 인증 완료로 둔다.
        self.assertEqual(self.sql(f"SELECT COUNT(*) FROM local_credential WHERE password_hash='{HASH}';"), "101")
        self.assertEqual(self.sql("SELECT COUNT(*) FROM member WHERE email_verified_at IS NOT NULL;"), "101")
        self.assertEqual(self.sql("SELECT s.name FROM spaces s JOIN slotkey_sample_registry r ON r.kind='SPACE' AND r.row_id=s.id WHERE r.seed_key='pangyo-01';"),
                         "[판교] 차콜 포커스룸")
        self.assertEqual(self.sql("SELECT COUNT(*) FROM spaces WHERE name NOT REGEXP '^\\\\[(판교|하남|강남)\\\\] ';"), "0")

    def test_old_names_are_renamed_on_rerun(self):
        self.run_routine("setup")
        self.sql("UPDATE spaces SET name='[샘플] 판교 차콜 포커스룸' WHERE id=1;")
        self.run_routine("setup")
        self.assertEqual(self.sql("SELECT name FROM spaces WHERE id=1;"), "[판교] 차콜 포커스룸")

    def test_clean_removes_rows_added_by_newer_features_then_reset(self):
        self.run_routine("setup")
        # 샘플 회원 1번이 충전·문의·대기·알림을 남긴 상태. 회원 FK가 걸린 행이 남으면 clean이 실패한다.
        self.sql("""INSERT INTO charge_order(order_id,member_id,amount,status,client_idempotency_key,created_at,updated_at)
VALUES('chg_local_test',1,1000,'PAID','idem-local-test',NOW(),NOW());
INSERT INTO credit_transaction(member_id,amount,type,balance_after,charge_order_id,created_at)
VALUES(1,1000,'TOP_UP',1001000,LAST_INSERT_ID(),NOW());
INSERT INTO inquiries(member_id,title,content,status,created_at) VALUES(1,'문의','내용','WAITING',NOW());
INSERT INTO waitlist_entry(member_id,space_id,start_time,end_time,status,active,created_at)
VALUES(1,1,'2030-01-01 10:00:00','2030-01-01 11:00:00','WAITING',1,NOW());
INSERT INTO notification(member_id,waitlist_entry_id,event_id,message,created_at)
VALUES(1,LAST_INSERT_ID(),'00000000-0000-4000-8000-000000000001','빈자리',NOW());""")
        self.run_routine("clean")
        self.assertEqual(self.sql("""SELECT COUNT(*) FROM member; SELECT COUNT(*) FROM spaces; SELECT COUNT(*) FROM local_credential;
SELECT COUNT(*) FROM charge_order; SELECT COUNT(*) FROM inquiries; SELECT COUNT(*) FROM waitlist_entry; SELECT COUNT(*) FROM notification;"""),
                         "0\n0\n0\n0\n0\n0\n0")
        self.run_routine("setup")
        self.assertEqual(self.sql(f"SELECT COUNT(*) FROM member; SELECT COUNT(*) FROM local_credential WHERE password_hash='{HASH}';"), "101\n101")

    def test_clean_refuses_when_sample_admin_answered_regular_inquiry(self):
        self.run_routine("setup")
        admin = self.sql("SELECT row_id FROM slotkey_sample_registry WHERE seed_key='admin-000';")
        self.sql(f"""INSERT INTO member(email,nickname,role,status,balance,created_at,updated_at)
VALUES('regular@test.com','일반','USER','ACTIVE',0,NOW(),NOW());
INSERT INTO inquiries(member_id,title,content,status,answer_content,answered_by_member_id,answered_at,created_at)
VALUES(LAST_INSERT_ID(),'문의','내용','ANSWERED','답변',{admin},NOW(),NOW());""")
        self.run_routine("clean", succeeds=False)
        self.assertEqual(self.sql("SELECT COUNT(*) FROM member; SELECT COUNT(*) FROM inquiries;"), "102\n1")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--mysql-container", help="검증용 MySQL 8.4 컨테이너 이름 (root, 빈 비밀번호)")
    args, remaining = parser.parse_known_args()
    CONTAINER = args.mysql_container
    if CONTAINER:
        LocalSetupTest.__unittest_skip__ = False
    unittest.main(argv=[__file__, *remaining], verbosity=2)
