package com.hss.scalecart.repository;

import com.hss.scalecart.entity.DeadLetterEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, UUID> {

    // DeadLetterEventRepository.java
    List<DeadLetterEvent> findByResolvedFalseOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT d FROM DeadLetterEvent d WHERE d.resolved = false AND d.createdAt < :cursor ORDER BY d.createdAt DESC")
    List<DeadLetterEvent> findUnresolvedBefore(@Param("cursor") LocalDateTime cursor, Pageable pageable);
}