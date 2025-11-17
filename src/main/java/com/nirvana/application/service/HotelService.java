package com.nirvana.application.service;

import com.nirvana.application.model.Hotel;
import com.nirvana.application.model.dto.HotelDTO;
import com.nirvana.application.model.dto.HotelRegistrationDTO;

import java.util.List;
import java.util.Optional;

public interface HotelService {

    Hotel saveHotel(HotelRegistrationDTO hotelRegistrationDTO);

    HotelDTO findHotelDtoByName(String name);

    HotelDTO findHotelDtoById(Long id);

    Optional<Hotel> findHotelById(Long id);

    List<HotelDTO> findAllHotels();

    HotelDTO updateHotel(HotelDTO hotelDTO);

    void deleteHotelById(Long id);

    List<Hotel> findAllHotelsByManagerId(Long managerId);

    List<HotelDTO> findAllHotelDtosByManagerId(Long managerId);

    HotelDTO findHotelByIdAndManagerId(Long hotelId, Long managerId);

    HotelDTO updateHotelByManagerId(HotelDTO hotelDTO, Long managerId);

    void deleteHotelByIdAndManagerId(Long hotelId, Long managerId);

    HotelDTO mapHotelToHotelDto(Hotel hotel);

}
