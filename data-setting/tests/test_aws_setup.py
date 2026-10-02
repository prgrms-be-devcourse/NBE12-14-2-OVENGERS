#!/usr/bin/env python3
"""가짜 CLI 흐름 검사 + 명시한 별도 MySQL 컨테이너의 임시 DB에서 SQL 검증."""
import argparse
import os
from pathlib import Path
import pty
import re
import select
import subprocess
import sys
import tempfile
import time
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[1]
HASH = "$2b$12$kRiVg0sZNHrmMtl9Q7eIguuqTOc0vQY51A/REFpC5sUmr7zyRzM8u"
CONTAINER = None


class PasswordTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        try:
            import bcrypt
        except ImportError:
            raise unittest.SkipTest("bcrypt가 필요합니다. 또는 --bcrypt-path로 임시 설치 경로를 지정하세요")
        cls.bcrypt = bcrypt

    def enter_password(self, values):
        master, slave = pty.openpty()
        env = dict(os.environ, PYTHONPATH=os.pathsep.join(sys.path))
        process = subprocess.Popen([sys.executable, str(ROOT / "aws-password.py")],
                                   stdin=slave, stderr=slave, stdout=subprocess.PIPE, env=env)
        os.close(slave)
        terminal = b""
        try:
            prompts = ["새 AWS 샘플 계정 공통 비밀번호", "비밀번호 확인"]
            for prompt, value in zip(prompts, values):
                deadline = time.monotonic() + 5
                while prompt.encode() not in terminal:
                    if time.monotonic() >= deadline:
                        self.fail("숨김 입력 프롬프트를 받지 못했습니다")
                    if select.select([master], [], [], 0.1)[0]:
                        terminal += os.read(master, 4096)
                os.write(master, value.encode() + b"\n")
            stdout, _ = process.communicate(timeout=5)
            while select.select([master], [], [], 0)[0]:
                try:
                    chunk = os.read(master, 4096)
                    if not chunk:
                        break
                    terminal += chunk
                except OSError:
                    break
            return process.returncode, stdout.decode(), terminal.decode()
        finally:
            if process.poll() is None:
                process.kill()
                process.communicate()
            os.close(master)

    def test_hidden_input_creates_matching_hash_without_plaintext(self):
        password = "study-only-password-2026"
        code, stdout, terminal = self.enter_password([password, password])
        self.assertEqual(code, 0, terminal)
        self.assertTrue(self.bcrypt.checkpw(password.encode(), stdout.strip().encode()))
        self.assertNotIn(password, stdout + terminal)
        result = subprocess.run([sys.executable, str(ROOT / "aws-password.py")],
                                input=password, text=True, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")

    def test_mismatch_and_byte_limit_rejected(self):
        for values in (["study-only-password-2026", "different-password"], ["한" * 25], ["short"]):
            code, stdout, _ = self.enter_password(values)
            self.assertNotEqual(code, 0)
            self.assertEqual(stdout, "")


class ScriptTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="slotkey-aws-script-")
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.storage = self.base / "images"
        self.storage.mkdir()
        self.config = self.base / "client.cnf"
        self.config.write_text("[client]\npassword=private-test-value\n")
        self.config.chmod(0o600)
        self.hash_file = self.base / "password.hash"
        self.hash_file.write_text(HASH + "\n")
        self.hash_file.chmod(0o600)
        self.log = self.base / "calls"
        self.bin = self.base / "bin"
        self.bin.mkdir()
        cli = self.bin / "mysql"
        cli.write_text("""#!/usr/bin/env python3
import os, pathlib, sys
sql = sys.stdin.read()
assert sys.argv[1].startswith('--defaults-file=')
assert '--no-login-paths' in sys.argv and '--protocol=TCP' in sys.argv
assert '--ssl-mode=VERIFY_IDENTITY' in sys.argv
assert 'MYSQL_PWD' not in os.environ
with open(os.environ['FAKE_MYSQL_LOG'], 'a') as f:
    f.write(sql + '\\n')
if 'CREATE PROCEDURE' in sql:
    if os.environ.get('FAKE_SQL_FAIL'):
        sys.exit(9)
elif 'flyway_schema_history' in sql:
    print(os.environ.get('FAKE_FLYWAY_VERSION', '12'))
elif 'information_schema.tables' in sql:
    print('0')
""")
        cli.chmod(0o755)
        self.env = dict(os.environ, PATH=f"{self.bin}:{os.environ['PATH']}",
                        AWS_SAMPLE_MYSQL_CNF=str(self.config),
                        AWS_SAMPLE_PASSWORD_HASH_FILE=str(self.hash_file),
                        SPACE_IMAGE_STORAGE_ROOT=str(self.storage),
                        FAKE_MYSQL_LOG=str(self.log), MYSQL_PWD="must-not-leak")

    def run_script(self, action, succeeds=True):
        result = subprocess.run(["bash", str(ROOT / "aws-setup.sh"), action],
                                env=self.env, capture_output=True, text=True)
        self.assertEqual(result.returncode == 0, succeeds, result.stderr)
        self.assertNotIn("private-test-value", result.stdout + result.stderr)
        self.assertNotIn(HASH, result.stdout + result.stderr)
        return result

    def test_install_images_is_repeatable_without_database(self):
        self.run_script("images")
        self.run_script("images")
        self.assertEqual(len(list(self.storage.glob("*.jpg"))), 39)
        self.assertFalse(self.log.exists())
        name = "a5100000-0000-4000-8000-000000000001.jpg"
        self.assertEqual((self.storage / name).read_bytes(), (ROOT / "images/space-01.jpg").read_bytes())

    def test_image_conflict_prevents_database_mutation(self):
        (self.storage / "a5100000-0000-4000-8000-000000000039.jpg").write_text("custom")
        self.run_script("setup", succeeds=False)
        self.assertEqual(len(list(self.storage.glob("*.jpg"))), 1)
        self.assertNotIn("CREATE TABLE", self.log.read_text())
        self.assertFalse((self.storage / ".slotkey-aws-install-lock").exists())

    def test_status_does_not_write_or_install(self):
        self.run_script("status")
        self.assertNotRegex(self.log.read_text(), r"CREATE|UPDATE|DELETE|DROP")
        self.assertEqual(list(self.storage.iterdir()), [])

    def test_permissions_and_old_schema_rejected(self):
        self.config.chmod(0o644)
        self.run_script("setup", succeeds=False)
        self.assertFalse(self.log.exists())
        self.config.chmod(0o600)
        self.env["FAKE_FLYWAY_VERSION"] = "11"
        self.run_script("setup", succeeds=False)
        self.assertNotIn("CREATE TABLE", self.log.read_text())

    def test_sql_failure_cleans_procedure_and_temp_file(self):
        self.env["FAKE_SQL_FAIL"] = "1"
        self.env["TMPDIR"] = str(self.base)
        self.run_script("setup", succeeds=False)
        self.assertIn("DROP PROCEDURE IF EXISTS", self.log.read_text())
        self.assertEqual(list(self.base.glob("slotkey-aws-sql.*")), [])

    def test_update_requires_no_password(self):
        del self.env["AWS_SAMPLE_PASSWORD_HASH_FILE"]
        self.run_script("update")
        self.assertIn("SET @aws_sample_create_missing = 0;", self.log.read_text())

    def test_invalid_hash_rejected_before_database_write(self):
        self.hash_file.write_text("not-a-hash")
        self.run_script("setup", succeeds=False)
        self.assertNotIn("CREATE TABLE", self.log.read_text())
        self.assertEqual(list(self.storage.iterdir()), [])

    def test_symlink_image_rejected(self):
        (self.storage / "a5100000-0000-4000-8000-000000000001.jpg").symlink_to(ROOT / "images/space-01.jpg")
        self.run_script("images", succeeds=False)


@unittest.skipUnless(CONTAINER, "--mysql-container로 별도 검증 컨테이너를 지정하세요")
class DatabaseTest(unittest.TestCase):
    def setUp(self):
        self.database = "slotkey_aws_test_" + uuid.uuid4().hex[:12]
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

    def seed(self, create=True, succeeds=True):
        routine = "aws_seed_test_" + uuid.uuid4().hex[:12]
        source = (f"SET @aws_sample_create_missing={int(create)};\n"
                  f"SET @aws_sample_password_hash='{HASH}';\n"
                  + (ROOT / "sql/aws_setup.sql").read_text().replace("__ROUTINE__", routine))
        try:
            return self.sql(source, succeeds=succeeds)
        finally:
            self.sql(f"DROP PROCEDURE IF EXISTS `{routine}`;")

    def test_create_and_rerun_no_duplicate(self):
        self.seed()
        self.seed()
        self.assertEqual(self.sql("SELECT COUNT(*) FROM member; SELECT COUNT(*) FROM spaces; SELECT COUNT(*),SUM(amount) FROM credit_transaction;"), "101\n39\n100\t10000000")
        self.assertEqual(self.sql("SELECT COUNT(*) FROM member WHERE password_hash='" + HASH + "';"), "101")
        self.assertEqual(self.sql("SELECT COUNT(DISTINCT image_path),COUNT(*) FROM spaces WHERE image_path REGEXP '^/api/v1/space-images/a5100000-0000-4000-8000-[0-9]{12}[.]jpg$';"), "39\t39")
        status = self.sql((ROOT / "sql/aws_status.sql").read_text())
        self.assertIn("AWS 관리자\t1", status)
        self.assertEqual(status.count("\t13"), 3)

    def test_update_preserves_financial_and_reservation_state(self):
        self.seed()
        self.sql("""UPDATE member SET balance=101000,status='SUSPENDED',nickname='사용자수정',password_hash='changed' WHERE id=1;
INSERT INTO credit_transaction(member_id,amount,type,balance_after,created_at) VALUES(1,1000,'ADMIN_GRANT',101000,NOW());
INSERT INTO reservation(member_id,space_id,start_time,end_time,status,price_per_slot_snapshot,total_amount,created_at)
VALUES(1,1,'2030-01-01 10:00:00','2030-01-01 11:00:00','CONFIRMED',2000,4000,NOW());
INSERT INTO reservation_slot(reservation_id,space_id,slot_start) VALUES(1,1,'2030-01-01 10:00:00');
UPDATE spaces SET name='표시수정',image_path='/old.jpg',price_per_slot=9000,capacity=20,opening_time='09:00:00',closing_time='22:00:00',status='INACTIVE',version=7 WHERE id=1;""")
        before = self.sql("SELECT * FROM member; SELECT * FROM credit_transaction; SELECT * FROM reservation; SELECT * FROM reservation_slot;")
        self.seed(create=False)
        self.seed()
        self.assertEqual(self.sql("SELECT * FROM member; SELECT * FROM credit_transaction; SELECT * FROM reservation; SELECT * FROM reservation_slot;"), before)
        self.assertEqual(self.sql("SELECT price_per_slot,capacity,opening_time,closing_time,status,version FROM spaces WHERE id=1;"), "9000\t20\t09:00:00\t22:00:00\tINACTIVE\t7")
        self.assertEqual(self.sql("SELECT name LIKE '[AWS]%', image_path LIKE '/api/v1/space-images/%' FROM spaces WHERE id=1;"), "1\t1")

    def test_update_empty_and_setup_missing(self):
        self.seed(create=False)
        self.assertEqual(self.sql("SELECT COUNT(*) FROM member; SELECT COUNT(*) FROM spaces;"), "0\n0")
        self.seed()
        self.sql("DELETE FROM slotkey_sample_registry WHERE kind='SPACE' AND row_id=39; DELETE FROM spaces WHERE id=39;")
        self.seed(create=False)
        self.assertEqual(self.sql("SELECT COUNT(*) FROM spaces;"), "38")
        self.seed()
        self.assertEqual(self.sql("SELECT COUNT(*) FROM spaces;"), "39")

    def test_untracked_late_collision_rolls_back(self):
        self.sql(f"INSERT INTO member(email,password_hash,nickname,role,status,balance,created_at,updated_at) VALUES('aws100@amazon.com','{HASH}','existing','USER','ACTIVE',5,NOW(),NOW());")
        self.seed(succeeds=False)
        self.assertEqual(self.sql("SELECT COUNT(*) FROM member; SELECT COUNT(*) FROM spaces; SELECT COUNT(*) FROM credit_transaction; SELECT COUNT(*) FROM slotkey_sample_registry;"), "1\n0\n0\n0")
        self.assertEqual(self.sql("SELECT balance FROM member; SELECT IS_FREE_LOCK(CONCAT(DATABASE(),':slotkey-samples'));"), "5\n1")

    def test_local_admin_and_legacy_aws_admin(self):
        self.sql(f"""INSERT INTO member(email,password_hash,nickname,role,status,balance,created_at,updated_at)
VALUES('admin000@sample.com','{HASH}','local','ADMIN','ACTIVE',0,NOW(),NOW());
INSERT INTO slotkey_sample_registry VALUES('MEMBER','admin-000',1);""")
        self.seed()
        self.assertEqual(self.sql("SELECT m.email FROM member m JOIN slotkey_sample_registry r ON r.row_id=m.id WHERE r.kind='MEMBER' AND r.seed_key='admin-000';"), "admin000@sample.com")
        self.sql("DELETE FROM slotkey_sample_registry WHERE seed_key='admin-000'; UPDATE slotkey_sample_registry SET seed_key='admin-000' WHERE seed_key='aws-admin-000';")
        self.seed(create=False)
        self.assertEqual(self.sql("SELECT COUNT(*) FROM slotkey_sample_registry WHERE seed_key='aws-admin-000'; SELECT COUNT(*) FROM slotkey_sample_registry WHERE seed_key='admin-000';"), "1\n0")

    def test_dangling_registry_rejected(self):
        self.sql("INSERT INTO slotkey_sample_registry VALUES('SPACE','aws-pangyo-01',99999);")
        self.seed(succeeds=False)
        self.assertEqual(self.sql("SELECT COUNT(*) FROM member; SELECT COUNT(*) FROM spaces;"), "0\n0")

    def test_nontransactional_registry_rejected(self):
        self.sql("ALTER TABLE slotkey_sample_registry ENGINE=MyISAM;")
        self.seed(succeeds=False)
        self.assertEqual(self.sql("SELECT COUNT(*) FROM member; SELECT COUNT(*) FROM spaces;"), "0\n0")

    def test_registered_email_change_rolls_back_display_updates(self):
        self.seed()
        self.sql("UPDATE spaces SET name='유지할이름' WHERE id=1; UPDATE member SET email='changed@example.com' WHERE email='admin000@amazon.com';")
        self.seed(create=False, succeeds=False)
        self.assertEqual(self.sql("SELECT name FROM spaces WHERE id=1;"), "유지할이름")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--mysql-container", help="검증용 MySQL 8.4 컨테이너 이름 (root, 빈 비밀번호)")
    parser.add_argument("--bcrypt-path", help="검증용 bcrypt 임시 설치 디렉터리")
    args, remaining = parser.parse_known_args()
    if args.bcrypt_path:
        sys.path.insert(0, args.bcrypt_path)
    CONTAINER = args.mysql_container
    # Decorator가 모듈 로드 시 평가되므로 명시한 경우에만 DB 검사를 활성화합니다.
    if CONTAINER:
        DatabaseTest.__unittest_skip__ = False
    unittest.main(argv=[__file__, *remaining], verbosity=2)
