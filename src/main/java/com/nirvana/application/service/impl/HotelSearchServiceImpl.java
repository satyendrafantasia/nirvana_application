package com.nirvana.application.service.impl;

import com.nirvana.application.model.Spa;
import com.nirvana.application.model.dto.AddressDTO;
import com.nirvana.application.model.dto.SpaAvailabilityDTO;
import com.nirvana.application.model.dto.RoomDTO;
import com.nirvana.application.model.enums.RoomType;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.service.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpaSearchServiceImpl implements SpaSearchService {

    private final SpaRepository SpaRepository;
    private final AddressService addressService;
    private final RoomService roomService;
    private final AvailabilityService availabilityService;

    @Override
    public List<SpaAvailabilityDTO> findAvailableSpasByCityAndDate(String city, LocalDate checkinDate, LocalDate checkoutDate) {
        validateCheckinAndCheckoutDates(checkinDate, checkoutDate);

        log.info("Attempting to find Spas in {} with available rooms from {} to {}", city, checkinDate, checkoutDate);

        // Number of days between check-in and check-out
        Long numberOfDays = ChronoUnit.DAYS.between(checkinDate, checkoutDate);

        // 1. Fetch Spas that satisfy the criteria (min 1 available room throughout the booking range)
        List<Spa> SpasWithAvailableRooms = SpaRepository.findSpasWithAvailableRooms(city, checkinDate, checkoutDate, numberOfDays);

        // 2. Fetch Spas that don't have any availability records for the entire booking range
        List<Spa> SpasWithoutAvailabilityRecords = SpaRepository.findSpasWithoutAvailabilityRecords(city, checkinDate, checkoutDate);

        // 3. Fetch Spas with partial availability; some days with records meeting the criteria and some days without any records
        List<Spa> SpasWithPartialAvailabilityRecords = SpaRepository.findSpasWithPartialAvailabilityRecords(city, checkinDate, checkoutDate, numberOfDays);

        // Combine and deduplicate the Spas using a Set
        Set<Spa> combinedSpas = new HashSet<>(SpasWithAvailableRooms);
        combinedSpas.addAll(SpasWithoutAvailabilityRecords);
        combinedSpas.addAll(SpasWithPartialAvailabilityRecords);

        log.info("Successfully found {} Spas with available rooms", combinedSpas.size());

        // Convert the combined Spa list to DTOs for the response
        return combinedSpas.stream()
                .map(Spa -> mapSpaToSpaAvailabilityDto(Spa, checkinDate, checkoutDate))
                .collect(Collectors.toList());
    }

    @Override
    public SpaAvailabilityDTO findAvailableSpaById(Long SpaId, LocalDate checkinDate, LocalDate checkoutDate) {
        validateCheckinAndCheckoutDates(checkinDate, checkoutDate);

        log.info("Attempting to find Spa with ID {} with available rooms from {} to {}", SpaId, checkinDate, checkoutDate);

        Optional<Spa> SpaOptional = SpaRepository.findById(SpaId);
        if (SpaOptional.isEmpty()) {
            log.error("No Spa found with ID: {}", SpaId);
            throw new EntityNotFoundException("Spa not found");
        }

        Spa Spa = SpaOptional.get();
        return mapSpaToSpaAvailabilityDto(Spa, checkinDate, checkoutDate);
    }


    @Override
    public SpaAvailabilityDTO mapSpaToSpaAvailabilityDto(Spa Spa, LocalDate checkinDate, LocalDate checkoutDate) {
        List<RoomDTO> roomDTOs = Spa.getRooms().stream()
                .map(roomService::mapRoomToRoomDto)  // convert each Room to RoomDTO
                .collect(Collectors.toList());

        AddressDTO addressDTO = addressService.mapAddressToAddressDto(Spa.getAddress());
        
        SpaAvailabilityDTO SpaAvailabilityDTO = SpaAvailabilityDTO.builder()
                .id(Spa.getId())
                .name(Spa.getName())
                .addressDTO(addressDTO)
                .roomDTOs(roomDTOs)
                .build();
        
        // For each room type, find the minimum available rooms across the date range
        int maxAvailableSingleRooms = Spa.getRooms().stream()
                .filter(room -> room.getRoomType() == RoomType.SINGLE)
                .mapToInt(room -> availabilityService.getMinAvailableRooms(room.getId(), checkinDate, checkoutDate))
                .max()
                .orElse(0); // Assume no single rooms if none match the filter
        SpaAvailabilityDTO.setMaxAvailableSingleRooms(maxAvailableSingleRooms);

        int maxAvailableDoubleRooms = Spa.getRooms().stream()
                .filter(room -> room.getRoomType() == RoomType.DOUBLE)
                .mapToInt(room -> availabilityService.getMinAvailableRooms(room.getId(), checkinDate, checkoutDate))
                .max()
                .orElse(0); // Assume no double rooms if none match the filter
        SpaAvailabilityDTO.setMaxAvailableDoubleRooms(maxAvailableDoubleRooms);

        return SpaAvailabilityDTO;
    }

    private void validateCheckinAndCheckoutDates(LocalDate checkinDate, LocalDate checkoutDate) {
        if (checkinDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Check-in date cannot be in the past");
        }
        if (checkoutDate.isBefore(checkinDate.plusDays(1))) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }
    }

}