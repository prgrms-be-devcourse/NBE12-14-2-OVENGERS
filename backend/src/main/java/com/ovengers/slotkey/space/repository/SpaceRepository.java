package com.ovengers.slotkey.space.repository;

import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpaceRepository extends JpaRepository<Space, Long> {

    @Query("SELECT s FROM Space s " +
            "WHERE (:status IS NULL OR s.status = :status) " +
            "AND (:keyword IS NULL OR :keyword = '' OR s.name " +
            "LIKE %:keyword% OR s.description LIKE %:keyword%)")
    Page<Space> searchSpacesByNameOrDescription(
            @Param("keyword") String keyword,
            @Param("status") SpaceStatus status,
            Pageable pageable);

    Page<Space> findAllByStatus(SpaceStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Space s WHERE s.id = :id")
    Optional<Space> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT s FROM Space s WHERE s.id = :id")
    Optional<Space> findByIdForShare(@Param("id") Long id);

    /**
     * 공간 찾기 화면(오피스 찾기) 필터: 키워드/지역/가격 범위 + 특정 시간대 점유 공간 제외.
     * excludedSpaceIds는 시간대 필터를 쓰지 않을 때도 항상 채워서 넘긴다(빈 컬렉션 바인딩을 피하기 위해
     * SpaceQueryService가 매치되지 않는 sentinel id 하나짜리 리스트를 기본값으로 넣는다).
     */
    @Query("SELECT s FROM Space s " +
            "WHERE s.status = :status " +
            "AND (:keyword IS NULL OR :keyword = '' OR s.name LIKE %:keyword% OR s.description LIKE %:keyword%) " +
            "AND (:location IS NULL OR :location = '' OR s.location LIKE %:location%) " +
            "AND (:minPrice IS NULL OR s.pricePerSlot >= :minPrice) " +
            "AND (:maxPrice IS NULL OR s.pricePerSlot <= :maxPrice) " +
            "AND s.id NOT IN :excludedSpaceIds")
    Page<Space> searchSpaces(
            @Param("status") SpaceStatus status,
            @Param("keyword") String keyword,
            @Param("location") String location,
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
            @Param("excludedSpaceIds") java.util.List<Long> excludedSpaceIds,
            Pageable pageable);
}
