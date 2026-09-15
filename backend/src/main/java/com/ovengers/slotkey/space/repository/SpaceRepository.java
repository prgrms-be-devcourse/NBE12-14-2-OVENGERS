package com.ovengers.slotkey.space.repository;

import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
