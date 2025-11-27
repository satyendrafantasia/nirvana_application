package com.nirvana.application.repository;
import com.nirvana.application.model.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    java.util.List<MediaAsset> findBySpaIdOrderByPositionAscIdAsc(Long spaId);
}
