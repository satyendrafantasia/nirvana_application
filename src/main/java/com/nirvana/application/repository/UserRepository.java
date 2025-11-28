package com.nirvana.application.repository;


import com.nirvana.application.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    User findByUsername(String username);

    boolean existsByUsername(String username);

    java.util.Optional<User> findByEmail(String email);

    java.util.Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    long countByDataErasureRequestedAtIsNotNull();
}
