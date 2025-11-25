package com.nirvana.application.repository;

import com.nirvana.application.model.ContentVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentVariantRepository extends JpaRepository<ContentVariant, Long> {

    List<ContentVariant> findByIsActiveTrueOrderByCreatedAtDesc();

    List<ContentVariant> findByIsActiveTrueAndExperimentKeyOrderByCreatedAtDesc(String experimentKey);
}
