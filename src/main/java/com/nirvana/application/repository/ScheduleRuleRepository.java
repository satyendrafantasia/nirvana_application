package com.nirvana.application.repository;

import com.nirvana.application.model.ScheduleRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleRuleRepository extends JpaRepository<ScheduleRule, Long> {

    /**
     * Find rules that apply to a spa on a given calendar date + weekday.
     *
     * - weekday: 1–7 (Mon..Sun) – matches your ScheduleRule.weekday (SMALLINT)
     * - appliesFrom / appliesTo may be null = "forever"
     */
    @Query("""
        SELECT r FROM ScheduleRule r
        WHERE r.spa.id = :spaId
          AND r.weekday = :weekday
          AND (r.appliesFrom IS NULL OR r.appliesFrom <= :date)
          AND (r.appliesTo   IS NULL OR r.appliesTo   >= :date)
        """)
    List<ScheduleRule> findApplicableRulesForDay(
            @Param("spaId") Long spaId,
            @Param("weekday") short weekday,
            @Param("date") LocalDate date
    );
}
