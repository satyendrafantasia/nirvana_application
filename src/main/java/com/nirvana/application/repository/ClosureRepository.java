package com.nirvana.application.repository;

import com.nirvana.application.model.Closure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface ClosureRepository extends JpaRepository<Closure, Long> {

    @Query("""
        SELECT c FROM Closure c
        WHERE c.spa.id = :spaId
          AND (
                (c.startTs <= :endTs AND c.endTs >= :startTs)
              )
    """)
    List<Closure> findClosuresOverlapping(@Param("spaId") Long spaId,
                                          @Param("startTs") OffsetDateTime startTs,
                                          @Param("endTs") OffsetDateTime endTs);

    List<Closure> findByEndTsAfter(OffsetDateTime cutoff);
}
