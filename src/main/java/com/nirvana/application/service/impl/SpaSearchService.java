// java
package com.nirvana.application.service.impl;

import com.nirvana.application.model.*;
import com.nirvana.application.model.dto.*;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import java.util.List;
import com.nirvana.application.model.Spa;
import static com.nirvana.application.repository.spec.SpaAddressSpecifications.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class SpaSearchService {

    private final SpaRepository spaRepository;

    public List<Spa> searchSpas(String city,
                                String countryCode,
                                String localityLike,
                                String freeText,
                                Double minLat,
                                Double maxLat,
                                Double minLon,
                                Double maxLon) {

        // Use a base "conjunction" specification (always true) and then chain conditions
        Specification<Spa> spec = (root, query, cb) -> cb.conjunction();

        if (city != null && !city.isBlank()) {
            spec = spec.and(cityEquals(city));
        }
        if (countryCode != null && !countryCode.isBlank()) {
            spec = spec.and(countryCodeEquals(countryCode));
        }
        if (localityLike != null && !localityLike.isBlank()) {
            spec = spec.and(localityLike(localityLike));
        }
        if (freeText != null && !freeText.isBlank()) {
            spec = spec.and(addressContains(freeText));
        }
        if (minLat != null || maxLat != null || minLon != null || maxLon != null) {
            spec = spec.and(withinLatLonBox(minLat, maxLat, minLon, maxLon));
        }

        return spaRepository.findAll(spec);
    }
}
