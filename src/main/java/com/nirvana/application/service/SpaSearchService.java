package com.nirvana.application.service;

import com.nirvana.application.model.Spa;
import com.nirvana.application.model.dto.SpaAvailabilityDTO;

import java.time.LocalDate;
import java.util.List;

public interface SpaSearchService {

    List<SpaAvailabilityDTO> findAvailableSpasByCityAndDate(String city, LocalDate checkinDate, LocalDate checkoutDate);

    SpaAvailabilityDTO findAvailableSpaById(Long SpaId, LocalDate checkinDate, LocalDate checkoutDate);

    SpaAvailabilityDTO mapSpaToSpaAvailabilityDto(Spa Spa, LocalDate checkinDate, LocalDate checkoutDate);
}
