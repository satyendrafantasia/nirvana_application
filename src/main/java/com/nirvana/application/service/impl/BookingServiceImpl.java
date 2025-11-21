package com.nirvana.application.service.impl;

import com.nirvana.application.model.*;
import com.nirvana.application.model.dto.AddressDTO;
import com.nirvana.application.model.dto.BookingDTO;
import com.nirvana.application.model.dto.BookingInitiationDTO;
import com.nirvana.application.model.dto.RoomSelectionDTO;
import com.nirvana.application.model.enums.PaymentMethod;
import com.nirvana.application.repository.BookingRepository;
import com.nirvana.application.service.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final AvailabilityService availabilityService;
    private final PaymentService paymentService;
    private final CustomerService customerService;
    private final SpaService SpaService;


    @Override
    @Transactional
    public Booking saveBooking(BookingInitiationDTO bookingInitiationDTO, Long userId) {
        validateBookingDates(bookingInitiationDTO.getCheckinDate(), bookingInitiationDTO.getCheckoutDate());

        Customer customer = customerService.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with user ID: " + userId));

        Spa Spa = SpaService.findSpaById(bookingInitiationDTO.getSpaId())
                .orElseThrow(() -> new EntityNotFoundException("Spa not found with ID: " + bookingInitiationDTO.getSpaId()));

        Booking booking = mapBookingInitDtoToBookingModel(bookingInitiationDTO, customer, Spa);

        return bookingRepository.save(booking);
    }

    @Override
    @Transactional
    public BookingDTO confirmBooking(BookingInitiationDTO bookingInitiationDTO, Long customerId) {
        Booking savedBooking = this.saveBooking(bookingInitiationDTO, customerId);
        Payment savedPayment = paymentService.savePayment(bookingInitiationDTO, savedBooking);
        savedBooking.setPayment(savedPayment);
        bookingRepository.save(savedBooking);
        availabilityService.updateAvailabilities(bookingInitiationDTO.getSpaId(), bookingInitiationDTO.getCheckinDate(),
                bookingInitiationDTO.getCheckoutDate(), bookingInitiationDTO.getRoomSelections());
        return mapBookingModelToBookingDto(savedBooking);
    }

    @Override
    public List<BookingDTO> findAllBookings() {
        List<Booking> bookings = bookingRepository.findAll();
        return bookings.stream()
                .map(this::mapBookingModelToBookingDto)
                .collect(Collectors.toList());
    }

    @Override
    public BookingDTO findBookingById(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found with ID: " + bookingId));
        return mapBookingModelToBookingDto(booking);
    }

    @Override
    public List<BookingDTO> findBookingsByCustomerId(Long customerId) {
        List<Booking> bookingDTOs = bookingRepository.findBookingsByCustomerId(customerId);
        return bookingDTOs.stream()
                .map(this::mapBookingModelToBookingDto)
                .sorted(Comparator.comparing(BookingDTO::getCheckinDate))
                .collect(Collectors.toList());
    }

    @Override
    public BookingDTO findBookingByIdAndCustomerId(Long bookingId, Long customerId) {
        Booking booking = bookingRepository.findBookingByIdAndCustomerId(bookingId, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found with ID: " + bookingId));
        return mapBookingModelToBookingDto(booking);
    }

    @Override
    public List<BookingDTO> findBookingsByManagerId(Long managerId) {
        List<Spa> Spas = SpaService.findAllSpasByManagerId(managerId);
        return Spas.stream()
                .flatMap(Spa -> bookingRepository.findBookingsBySpaId(Spa.getId()).stream())
                .map(this::mapBookingModelToBookingDto)
                .collect(Collectors.toList());
    }

    @Override
    public BookingDTO findBookingByIdAndManagerId(Long bookingId, Long managerId) {
        Booking booking = bookingRepository.findBookingByIdAndSpa_SpaManagerId(bookingId, managerId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found with ID: " + bookingId + " and manager ID: " + managerId));
        return mapBookingModelToBookingDto(booking);
    }

    private void validateBookingDates(LocalDate checkinDate, LocalDate checkoutDate) {
        if (checkinDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Check-in date cannot be in the past");
        }
        if (checkoutDate.isBefore(checkinDate.plusDays(1))) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }
    }

    @Override
    public BookingDTO mapBookingModelToBookingDto(Booking booking) {
        AddressDTO addressDto = AddressDTO.builder()
                .addressLine(booking.getSpa().getAddress().getAddressLine())
                .city(booking.getSpa().getAddress().getCity())
                .country(booking.getSpa().getAddress().getCountry())
                .build();

        List<RoomSelectionDTO> roomSelections = booking.getBookedRooms().stream()
                .map(room -> RoomSelectionDTO.builder()
                        .roomType(room.getRoomType())
                        .count(room.getCount())
                        .build())
                .collect(Collectors.toList());

        User customerUser = booking.getCustomer().getUser();

        return BookingDTO.builder()
                .id(booking.getId())
                .confirmationNumber(booking.getConfirmationNumber())
                .bookingDate(booking.getBookingDate())
                .customerId(booking.getCustomer().getId())
                .SpaId(booking.getSpa().getId())
                .checkinDate(booking.getCheckinDate())
                .checkoutDate(booking.getCheckoutDate())
                .roomSelections(roomSelections)
                .totalPrice(booking.getPayment().getTotalPrice())
                .SpaName(booking.getSpa().getName())
                .SpaAddress(addressDto)
                .customerName(customerUser.getName() + " " + customerUser.getLastName())
                .customerEmail(customerUser.getUsername())
                .paymentStatus(booking.getPayment().getPaymentStatus())
                .paymentMethod(PaymentMethod.valueOf(booking.getPayment().getPaymentMethod()))
                .build();
    }

    private Booking mapBookingInitDtoToBookingModel(BookingInitiationDTO bookingInitiationDTO, Customer customer, Spa Spa) {
        Booking booking = Booking.builder()
                .customer(customer)
                .spa(Spa)
                .checkinDate(bookingInitiationDTO.getCheckinDate())
                .checkoutDate(bookingInitiationDTO.getCheckoutDate())
                .build();

        for (RoomSelectionDTO roomSelection : bookingInitiationDTO.getRoomSelections()) {
            if (roomSelection.getCount() > 0) {
                BookedRoom bookedRoom = BookedRoom.builder()
                        .booking(booking)
                        .roomType(roomSelection.getRoomType())
                        .count(roomSelection.getCount())
                        .build();
                booking.getBookedRooms().add(bookedRoom);
            }
        }

        return booking;
    }

}
