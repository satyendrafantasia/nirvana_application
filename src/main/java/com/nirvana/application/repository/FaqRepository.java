package com.nirvana.application.repository;

import com.nirvana.application.model.Faq;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    List<Faq> findByIsActiveTrueOrderByCreatedAtDesc();

    List<Faq> findByIsActiveTrueAndCategoryOrderByCreatedAtDesc(String category);
}
