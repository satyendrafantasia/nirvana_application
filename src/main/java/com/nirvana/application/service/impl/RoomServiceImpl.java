package com.nirvana.application.service.impl;

import com.nirvana.application.model.Spa;
import com.nirvana.application.model.Room;
import com.nirvana.application.model.dto.RoomDTO;
import com.nirvana.application.repository.RoomRepository;
import com.nirvana.application.service.RoomService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;

    @Override
    public Room saveRoom(RoomDTO roomDTO, Spa Spa) {
        log.info("Attempting to save a new room: {}", roomDTO);
        Room room = mapRoomDtoToRoom(roomDTO, Spa);
        room = roomRepository.save(room);
        log.info("Successfully saved room with ID: {}", room.getId());
        return room;
    }

    @Override
    public List<Room> saveRooms(List<RoomDTO> roomDTOs, Spa Spa) {
        log.info("Attempting to save rooms: {}", roomDTOs);
        List<Room> rooms = roomDTOs.stream()
                .map(roomDTO -> saveRoom(roomDTO, Spa))
                .collect(Collectors.toList());
        log.info("Successfully saved rooms: {}", rooms);
        return rooms;
    }

    @Override
    public Optional<Room> findRoomById(Long id) {
        return roomRepository.findById(id);
    }

    @Override
    public List<Room> findRoomsBySpaId(Long SpaId) {
        return null;
    }


    @Override
    public Room updateRoom(RoomDTO roomDTO) {
        log.info("Attempting to update room with ID: {}", roomDTO.getId());
        Room existingRoom = roomRepository.findById(roomDTO.getId())
                .orElseThrow(() -> new EntityNotFoundException("Room not found"));

        log.info("Room with ID: {} found", roomDTO.getId());

        existingRoom.setRoomType(roomDTO.getRoomType());
        existingRoom.setRoomCount(roomDTO.getRoomCount());
        existingRoom.setPricePerNight(roomDTO.getPricePerNight());

        Room updatedRoom = roomRepository.save(existingRoom);
        log.info("Successfully updated room with ID: {}", existingRoom.getId());
        return updatedRoom;
    }

    @Override
    public void deleteRoom(Long id) {
        if (!roomRepository.existsById(id)) {
            throw new EntityNotFoundException("Room not found with id: " + id);
        }
        roomRepository.deleteById(id);
        log.info("Deleted room with ID: {}", id);
    }

    @Override
    public Room mapRoomDtoToRoom(RoomDTO roomDTO, Spa Spa) {
        log.debug("Mapping RoomDTO to Room: {}", roomDTO);
        Room room = Room.builder()
                .spa(Spa)
                .roomType(roomDTO.getRoomType())
                .roomCount(roomDTO.getRoomCount())
                .pricePerNight(roomDTO.getPricePerNight())
                .build();
        log.debug("Mapped Room: {}", room);
        return room;
    }

    @Override
    public RoomDTO mapRoomToRoomDto(Room room) {
        return RoomDTO.builder()
                .id(room.getId())
                .SpaId(room.getSpa() != null ? room.getSpa().getId() : null)
                .roomType(room.getRoomType())
                .roomCount(room.getRoomCount())
                .pricePerNight(room.getPricePerNight())
                .build();
    }
}
