package com.nirvana.application.repository;

import com.nirvana.application.model.SpaManager;
import com.nirvana.application.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpaManagerRepository extends JpaRepository<SpaManager, Long> {

    Optional<SpaManager> findByUser(User user);
}
