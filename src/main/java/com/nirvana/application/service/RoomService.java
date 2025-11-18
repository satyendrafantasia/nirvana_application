package com.nirvana.application.service;

import com.nirvana.application.model.Spa;
import com.nirvana.application.model.Room;
import com.nirvana.application.model.dto.RoomDTO;

import java.util.List;
import java.util.Optional;

public interface RoomService {

    Room saveRoom(RoomDTO roomDTO, Spa Spa);

    List<Room> saveRooms(List<RoomDTO> roomDTOs, Spa Spa);

    Optional<Room> findRoomById(Long id);

    List<Room> findRoomsBySpaId(Long SpaId);

    Room updateRoom(RoomDTO roomDTO);

    void deleteRoom(Long id);

    Room mapRoomDtoToRoom(RoomDTO roomDTO, Spa Spa);

    RoomDTO mapRoomToRoomDto(Room room);

}
