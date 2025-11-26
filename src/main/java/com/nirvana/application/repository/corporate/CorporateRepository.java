package com.nirvana.application.repository.corporate;

import com.nirvana.application.model.corporate.Corporate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CorporateRepository extends JpaRepository<Corporate, Long> {
    Optional<Corporate> findByDomain(String domain);
}
