# 2026-09-17 이태호
# 테이블명 space → spaces (API 리소스명 /spaces 와 통일).
# 이미 적용된 V2~V5 는 Flyway checksum 때문에 수정하지 않고, 새 버전으로 이름만 바꾼다.
# InnoDB 는 RENAME 시 space 를 참조하던 FK(reservation, reservation_slot, V5 출입 테이블)를 자동으로 따라간다.
RENAME TABLE space TO spaces;
