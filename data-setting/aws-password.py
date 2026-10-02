#!/usr/bin/env python3
"""터미널 숨김 입력을 BCrypt로 변환합니다. 평문은 파일/명령 인자로 전달하지 않습니다."""
import getpass
import sys


def main():
    if not sys.stdin.isatty():
        sys.exit("숨김 입력에는 터미널이 필요합니다. 무인 실행은 AWS_SAMPLE_PASSWORD_HASH_FILE을 사용하세요.")
    try:
        import bcrypt
    except ImportError:
        sys.exit("bcrypt가 필요합니다. Ubuntu: sudo apt install python3-bcrypt")
    password = getpass.getpass("새 AWS 샘플 계정 공통 비밀번호 (12자 이상): ")
    if len(password) < 12 or len(password.encode("utf-8")) > 72:
        sys.exit("비밀번호는 12자 이상, UTF-8 기준 72바이트 이하여야 합니다.")
    if password != getpass.getpass("비밀번호 확인: "):
        sys.exit("비밀번호가 일치하지 않습니다.")
    print(bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt(rounds=12)).decode("ascii"))


if __name__ == "__main__":
    main()
