package com.nirvana.application.service.impl;

import com.nirvana.application.exception.SpaAlreadyExistsException;
import com.nirvana.application.model.*;
import com.nirvana.application.model.dto.*;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.service.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpaServiceImpl implements SpaService {

    private final SpaRepository SpaRepository;
    private final AddressService addressService;
    private final RoomService roomService;
    private final UserService userService;
    private final SpaManagerService SpaManagerService;

    @Override
    @Transactional
    public Spa saveSpa(SpaRegistrationDTO SpaRegistrationDTO) {
        log.info("Attempting to save a new Spa: {}", SpaRegistrationDTO.toString());

        Optional<Spa> existingSpa = SpaRepository.findByName(SpaRegistrationDTO.getName());
        if (existingSpa.isPresent()) {
            throw new SpaAlreadyExistsException("This Spa name is already registered!");
        }

        Spa Spa = mapSpaRegistrationDtoToSpa(SpaRegistrationDTO);

        Address savedAddress = addressService.saveAddress(SpaRegistrationDTO.getAddressDTO());
        Spa.setAddress(savedAddress);

        // Get the username of the currently logged-in Spa manager
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        // Retrieve the Spa Manager associated with this username
        SpaManager SpaManager = SpaManagerService.findByUser(userService.findUserByUsername(username));
        Spa.setSpaManager(SpaManager);

        // Saving Spa to be able to bind rooms to Spa id
        Spa = SpaRepository.save(Spa);

        List<Room> savedRooms = roomService.saveRooms(SpaRegistrationDTO.getRoomDTOs(), Spa);
//        Spa.setRooms(savedRooms);

        Spa savedSpa = SpaRepository.save(Spa);
        log.info("Successfully saved new Spa with ID: {}", Spa.getId());
        return savedSpa;
    }

    @Override
    public SpaDTO findSpaDtoByName(String name) {
        Spa Spa = SpaRepository.findByName(name)
                .orElseThrow(() -> new EntityNotFoundException("Spa not found"));
        return mapSpaToSpaDto(Spa);
    }

    @Override
    public SpaDTO findSpaDtoById(Long id) {
        Spa Spa = SpaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Spa not found"));
        return mapSpaToSpaDto(Spa);
    }

    @Override
    public Optional<Spa> findSpaById(Long id) {
        return SpaRepository.findById(id);
    }

    @Override
    public List<SpaDTO> findAllSpas() {
        List<Spa> Spas = SpaRepository.findAll();
        return Spas.stream()
                .map(this::mapSpaToSpaDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SpaDTO updateSpa(SpaDTO SpaDTO) {
        log.info("Attempting to update Spa with ID: {}", SpaDTO.getId());

        Spa existingSpa = SpaRepository.findById(SpaDTO.getId())
                .orElseThrow(() -> new EntityNotFoundException("Spa not found"));

        if (SpaNameExistsAndNotSameSpa(SpaDTO.getName(), SpaDTO.getId())) {
            throw new SpaAlreadyExistsException("This Spa name is already registered!");
        }

        existingSpa.setName(SpaDTO.getName());

        Address updatedAddress = addressService.updateAddress(SpaDTO.getAddressDTO());
        existingSpa.setAddress(updatedAddress);

        SpaDTO.getRoomDTOs().forEach(roomService::updateRoom);

        SpaRepository.save(existingSpa);
        log.info("Successfully updated existing Spa with ID: {}", SpaDTO.getId());
        return mapSpaToSpaDto(existingSpa);
    }

    @Override
    public void deleteSpaById(Long id) {
        log.info("Attempting to delete Spa with ID: {}", id);
        SpaRepository.deleteById(id);
        log.info("Successfully deleted Spa with ID: {}", id);
    }
    @Override
    public List<Spa> findAllSpasByManagerId(Long managerId) {
        List<Spa> Spas = SpaRepository.findAllBySpaManager_Id(managerId);
        return (Spas != null) ? Spas : Collections.emptyList();
    }

    @Override
    public List<SpaDTO> findAllSpaDtosByManagerId(Long managerId) {
        List<Spa> Spas = SpaRepository.findAllBySpaManager_Id(managerId);
        if (Spas != null) {
            return Spas.stream()
                    .map(this::mapSpaToSpaDto)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public SpaDTO findSpaByIdAndManagerId(Long SpaId, Long managerId) {
        Spa Spa = SpaRepository.findByIdAndSpaManager_Id(SpaId, managerId)
                .orElseThrow(() -> new EntityNotFoundException("Spa not found"));
        return mapSpaToSpaDto(Spa);
    }

    @Override
    @Transactional
    public SpaDTO updateSpaByManagerId(SpaDTO SpaDTO, Long managerId) {
        log.info("Attempting to update Spa with ID: {} for Manager ID: {}", SpaDTO.getId(), managerId);

        Spa existingSpa = SpaRepository.findByIdAndSpaManager_Id(SpaDTO.getId(), managerId)
                .orElseThrow(() -> new EntityNotFoundException("Spa not found"));

        if (SpaNameExistsAndNotSameSpa(SpaDTO.getName(), SpaDTO.getId())) {
            throw new SpaAlreadyExistsException("This Spa name is already registered!");
        }

        existingSpa.setName(SpaDTO.getName());

        Address updatedAddress = addressService.updateAddress(SpaDTO.getAddressDTO());
        existingSpa.setAddress(updatedAddress);

        SpaDTO.getRoomDTOs().forEach(roomService::updateRoom);

        SpaRepository.save(existingSpa);
        log.info("Successfully updated existing Spa with ID: {} for Manager ID: {}", SpaDTO.getId(), managerId);
        return mapSpaToSpaDto(existingSpa);    }

    @Override
    public void deleteSpaByIdAndManagerId(Long SpaId, Long managerId) {
        log.info("Attempting to delete Spa with ID: {} for Manager ID: {}", SpaId, managerId);
        Spa Spa = SpaRepository.findByIdAndSpaManager_Id(SpaId, managerId)
                .orElseThrow(() -> new EntityNotFoundException("Spa not found"));
        SpaRepository.delete(Spa);
        log.info("Successfully deleted Spa with ID: {} for Manager ID: {}", SpaId, managerId);
    }

    private Spa mapSpaRegistrationDtoToSpa(SpaRegistrationDTO dto) {
        return Spa.builder()
                .name(formatText(dto.getName()))
                .build();
    }

    @Override
    public SpaDTO mapSpaToSpaDto(Spa Spa) {
//        List<RoomDTO> roomDTOs = Spa.getRooms().stream()
//                .map(roomService::mapRoomToRoomDto)  // convert each Room to RoomDTO
//                .collect(Collectors.toList());  // collect results to a list

        AddressDTO addressDTO = addressService.mapAddressToAddressDto(Spa.getAddress());

        return SpaDTO.builder()
                .id(Spa.getId())
                .name(Spa.getName())
                .addressDTO(addressDTO)
                .managerUsername(Spa.getSpaManager().getUser().getUsername())
                .build();
    }

    private boolean SpaNameExistsAndNotSameSpa(String name, Long SpaId) {
        Optional<Spa> existingSpaWithSameName = SpaRepository.findByName(name);
        return existingSpaWithSameName.isPresent() && !existingSpaWithSameName.get().getId().equals(SpaId);
    }

    private String formatText(String text) {
        return StringUtils.capitalize(text.trim());
    }

}

