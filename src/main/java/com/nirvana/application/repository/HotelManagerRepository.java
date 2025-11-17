package com.nirvana.application.repository;

import com.nirvana.application.model.HotelManager;
import com.nirvana.application.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HotelManagerRepository extends JpaRepository<HotelManager, Long> {

    Optional<HotelManager> findByUser(User user);
}
