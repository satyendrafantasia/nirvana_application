package com.nirvana.application.repository;

import com.nirvana.application.model.ProviderUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderUserRepository extends JpaRepository<ProviderUser, Long> {
}
