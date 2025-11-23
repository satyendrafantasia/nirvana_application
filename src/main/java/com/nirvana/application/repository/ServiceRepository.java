package com.nirvana.application.repository;

// src/main/java/com/nirvana/application/repository/ServiceRepository.java


import com.nirvana.application.model.Service;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceRepository extends JpaRepository<Service, Long> {

    List<Service> findBySpaIdAndIsActiveTrueOrderByNameAsc(Long spaId);
}
