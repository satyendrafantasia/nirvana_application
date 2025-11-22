package com.nirvana.application.repository;
import com.nirvana.application.model.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    Optional<MediaAsset> findFirstByEntityTypeAndEntityIdOrderByPositionAsc(String entityType, Long entityId);
}
