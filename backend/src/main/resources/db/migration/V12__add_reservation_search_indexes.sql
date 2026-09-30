-- 관리자 예약 검색(AdminReservationController)이 date/spaceId 조건으로 start_time을 필터링할 때
-- 인덱스가 없어 전체 스캔이 발생하던 문제를 해결한다. 부하테스트로 쌓인 대량 데이터에서 특히
-- 체감되는 문제였다(오늘 예약이 0건인 날에도 조회가 느림).
CREATE INDEX idx_reservation_start_time
    ON reservation (start_time);

CREATE INDEX idx_reservation_space_start_time
    ON reservation (space_id, start_time);
