package com.nirvana.application.service;

import com.nirvana.application.model.dto.RoomSelectionDTO;

import java.time.LocalDate;
import java.util.List;

public interface AvailabilityService {

    Integer getMinAvailableRooms(Long roomId, LocalDate checkinDate, LocalDate checkoutDate);

    void updateAvailabilities(long SpaId, LocalDate checkinDate, LocalDate checkoutDate, List<RoomSelectionDTO> roomSelections);

}
