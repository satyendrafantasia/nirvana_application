package com.nirvana.application.service;

import com.nirvana.application.model.Hotel;
import com.nirvana.application.model.dto.HotelAvailabilityDTO;

import java.time.LocalDate;
import java.util.List;

public interface HotelSearchService {

    List<HotelAvailabilityDTO> findAvailableHotelsByCityAndDate(String city, LocalDate checkinDate, LocalDate checkoutDate);

    HotelAvailabilityDTO findAvailableHotelById(Long hotelId, LocalDate checkinDate, LocalDate checkoutDate);

    HotelAvailabilityDTO mapHotelToHotelAvailabilityDto(Hotel hotel, LocalDate checkinDate, LocalDate checkoutDate);
}
