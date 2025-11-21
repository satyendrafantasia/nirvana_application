// java
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

    private final SpaRepository spaRepository;
    private final AddressService addressService;
    private final RoomService roomService;
    private final AvailabilityService availabilityService;

    @Override
    public List<SpaAvailabilityDTO> findAvailableSpasByCityAndDate(String city, LocalDate checkinDate, LocalDate checkoutDate) {
        validateCheckinAndCheckoutDates(checkinDate, checkoutDate);

        log.info("Attempting to find Spas in {} with available rooms from {} to {}", city, checkinDate, checkoutDate);

        // Number of days between check-in and check-out
        long numberOfDays = ChronoUnit.DAYS.between(checkinDate, checkoutDate);

        // 1. Fetch Spas that satisfy the criteria (min 1 available room throughout the booking range)
        List<Spa> spasWithAvailableRooms = spaRepository.findSpasWithAvailableRooms(city, checkinDate, checkoutDate, numberOfDays);

        // 2. Fetch Spas that don't have any availability records for the entire booking range
        List<Spa> spasWithoutAvailabilityRecords = spaRepository.findSpasWithoutAvailabilityRecords(city, checkinDate, checkoutDate);

        // 3. Fetch Spas with partial availability; some days with records meeting the criteria and some days without any records
        List<Spa> spasWithPartialAvailabilityRecords = spaRepository.findSpasWithPartialAvailabilityRecords(city, checkinDate, checkoutDate, numberOfDays);

        // Combine and deduplicate the Spas using a Set
        Set<Spa> combinedSpas = new HashSet<>(spasWithAvailableRooms);
        combinedSpas.addAll(spasWithoutAvailabilityRecords);
        combinedSpas.addAll(spasWithPartialAvailabilityRecords);

        log.info("Successfully found {} Spas with available rooms", combinedSpas.size());

        // Convert the combined Spa list to DTOs for the response
        return combinedSpas.stream()
                .map(spa -> mapSpaToSpaAvailabilityDto(spa, checkinDate, checkoutDate))
                .collect(Collectors.toList());
    }

    @Override
    public SpaAvailabilityDTO findAvailableSpaById(Long spaId, LocalDate checkinDate, LocalDate checkoutDate) {
        validateCheckinAndCheckoutDates(checkinDate, checkoutDate);

        log.info("Attempting to find Spa with ID {} with available rooms from {} to {}", spaId, checkinDate, checkoutDate);

        Optional<Spa> spaOptional = spaRepository.findById(spaId);
        if (spaOptional.isEmpty()) {
            log.error("No Spa found with ID: {}", spaId);
            throw new EntityNotFoundException("Spa not found");
        }

        Spa spa = spaOptional.get();
        return mapSpaToSpaAvailabilityDto(spa, checkinDate, checkoutDate);
    }


    @Override
    public SpaAvailabilityDTO mapSpaToSpaAvailabilityDto(Spa spa, LocalDate checkinDate, LocalDate checkoutDate) {
        List<RoomDTO> roomDTOs = spa.getRooms().stream()
                .map(roomService::mapRoomToRoomDto)  // convert each Room to RoomDTO
                .collect(Collectors.toList());

        AddressDTO addressDTO = addressService.mapAddressToAddressDto(spa.getAddress());

        SpaAvailabilityDTO spaAvailabilityDTO = SpaAvailabilityDTO.builder()
                .id(spa.getId())
                .name(spa.getName())
                .addressDTO(addressDTO)
                .roomDTOs(roomDTOs)
                .build();

        // For each room type, find the maximum available rooms across the date range (minimum available per day)
        int maxAvailableSingleRooms = spa.getRooms().stream()
                .filter(room -> room.getRoomType() == RoomType.SINGLE)
                .mapToInt(room -> availabilityService.getMinAvailableRooms(room.getId(), checkinDate, checkoutDate))
                .max()
                .orElse(0);
        spaAvailabilityDTO.setMaxAvailableSingleRooms(maxAvailableSingleRooms);

        int maxAvailableDoubleRooms = spa.getRooms().stream()
                .filter(room -> room.getRoomType() == RoomType.DOUBLE)
                .mapToInt(room -> availabilityService.getMinAvailableRooms(room.getId(), checkinDate, checkoutDate))
                .max()
                .orElse(0);
        spaAvailabilityDTO.setMaxAvailableDoubleRooms(maxAvailableDoubleRooms);

        return spaAvailabilityDTO;
    }

    private void validateCheckinAndCheckoutDates(LocalDate checkinDate, LocalDate checkoutDate) {
        if (checkinDate == null || checkoutDate == null) {
            throw new IllegalArgumentException("Check-in and check-out dates must be provided");
        }
        if (checkinDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Check-in date cannot be in the past");
        }
        if (checkoutDate.isBefore(checkinDate.plusDays(1))) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }
    }

}
