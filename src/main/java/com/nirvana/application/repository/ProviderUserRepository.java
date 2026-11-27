package com.nirvana.application.repository;

import com.nirvana.application.model.ProviderUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderUserRepository extends JpaRepository<ProviderUser, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select p from ProviderUser p where p.id = :provider and exists
                (select s.id from Slot s where s.spa.id = :spa and s.assignedProviderUser = p
                 and s.startTs < :end and s.endTs > :start)
            """)
    java.util.Optional<ProviderUser> findAssignedDuring(Long provider, Long spa,
            java.time.OffsetDateTime start, java.time.OffsetDateTime end);
}
