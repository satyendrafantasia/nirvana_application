package com.nirvana.application.repository;
import com.nirvana.application.model.MediaAsset;
import com.nirvana.application.model.enums.MediaType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    java.util.List<MediaAsset> findBySpaIdOrderByPositionAscIdAsc(Long spaId);

    Optional<MediaAsset> findFirstBySpaIdAndMediaTypeOrderByPositionAscIdAsc(Long spaId, MediaType mediaType);
}
