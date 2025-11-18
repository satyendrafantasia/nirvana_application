package com.nirvana.application.controller;

import com.nirvana.application.exception.SpaAlreadyExistsException;
import com.nirvana.application.model.dto.BookingDTO;
import com.nirvana.application.model.dto.SpaDTO;
import com.nirvana.application.model.dto.SpaRegistrationDTO;
import com.nirvana.application.model.dto.RoomDTO;
import com.nirvana.application.model.enums.RoomType;
import com.nirvana.application.service.BookingService;
import com.nirvana.application.service.SpaService;
import com.nirvana.application.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
@RequestMapping("/manager")
@RequiredArgsConstructor
@Slf4j
public class SpaManagerController {

    private final SpaService SpaService;
    private final UserService userService;
    private final BookingService bookingService;

    @GetMapping("/dashboard")
    public String dashboard() {
        return "Spamanager/dashboard";
    }

    @GetMapping("/Spas/add")
    public String showAddSpaForm(Model model) {
        SpaRegistrationDTO SpaRegistrationDTO = new SpaRegistrationDTO();

        // Initialize roomDTOs with SINGLE and DOUBLE room types
        RoomDTO singleRoom = new RoomDTO(null, null, RoomType.SINGLE, 0, 0.0);
        RoomDTO doubleRoom = new RoomDTO(null, null, RoomType.DOUBLE, 0, 0.0);
        SpaRegistrationDTO.getRoomDTOs().add(singleRoom);
        SpaRegistrationDTO.getRoomDTOs().add(doubleRoom);

        model.addAttribute("Spa", SpaRegistrationDTO);
        return "Spamanager/Spas-add";
    }

    @PostMapping("/Spas/add")
    public String addSpa(@Valid @ModelAttribute("Spa") SpaRegistrationDTO SpaRegistrationDTO, BindingResult result, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            log.warn("Spa creation failed due to validation errors: {}", result.getAllErrors());
            return "Spamanager/Spas-add";
        }
        try {
            SpaService.saveSpa(SpaRegistrationDTO);
            redirectAttributes.addFlashAttribute("message", "Spa (" + SpaRegistrationDTO.getName() + ") added successfully");
            return "redirect:/manager/Spas";
        } catch (SpaAlreadyExistsException e) {
            result.rejectValue("name", "Spa.exists", e.getMessage());
            return "Spamanager/Spas-add";
        }
    }

    @GetMapping("/Spas")
    public String listSpas(Model model) {
        Long managerId = getCurrentManagerId();
        List<SpaDTO> SpaList = SpaService.findAllSpaDtosByManagerId(managerId);
        model.addAttribute("Spas", SpaList);
        return "Spamanager/Spas";
    }

    @GetMapping("/Spas/edit/{id}")
    public String showEditSpaForm(@PathVariable Long id, Model model) {
        Long managerId = getCurrentManagerId();
        SpaDTO SpaDTO = SpaService.findSpaByIdAndManagerId(id, managerId);
        model.addAttribute("Spa", SpaDTO);
        return "Spamanager/Spas-edit";
    }

    @PostMapping("/Spas/edit/{id}")
    public String editSpa(@PathVariable Long id, @Valid @ModelAttribute("Spa") SpaDTO SpaDTO, BindingResult result, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "Spamanager/Spas-edit";
        }
        try {
            Long managerId = getCurrentManagerId();
            SpaDTO.setId(id);
            SpaService.updateSpaByManagerId(SpaDTO, managerId);
            redirectAttributes.addFlashAttribute("message", "Spa (ID: " + id + ") updated successfully");
            return "redirect:/manager/Spas";

        } catch (SpaAlreadyExistsException e) {
            result.rejectValue("name", "Spa.exists", e.getMessage());
            return "Spamanager/Spas-edit";
        } catch (EntityNotFoundException e) {
            result.rejectValue("id", "Spa.notfound", e.getMessage());
            return "Spamanager/Spas-edit";
        }
    }

    @PostMapping("/Spas/delete/{id}")
    public String deleteSpa(@PathVariable Long id) {
        Long managerId = getCurrentManagerId();
        SpaService.deleteSpaByIdAndManagerId(id, managerId);
        return "redirect:/manager/Spas";
    }

    @GetMapping("/bookings")
    public String listBookings(Model model, RedirectAttributes redirectAttributes) {
        try {
            Long managerId = getCurrentManagerId();
            List<BookingDTO> bookingDTOs = bookingService.findBookingsByManagerId(managerId);
            model.addAttribute("bookings", bookingDTOs);

            return "Spamanager/bookings";
        } catch (EntityNotFoundException e) {
            log.error("No bookings found for the provided manager ID", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Bookings not found. Please try again later.");
            return "redirect:/manager/dashboard";
        } catch (Exception e) {
            log.error("An error occurred while listing bookings", e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred. Please try again later.");
            return "redirect:/manager/dashboard";
        }
    }

    @GetMapping("/bookings/{id}")
    public String viewBookingDetails(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Long managerId = getCurrentManagerId();
            BookingDTO bookingDTO = bookingService.findBookingByIdAndManagerId(id, managerId);
            model.addAttribute("bookingDTO", bookingDTO);

            LocalDate checkinDate = bookingDTO.getCheckinDate();
            LocalDate checkoutDate = bookingDTO.getCheckoutDate();
            long durationDays = ChronoUnit.DAYS.between(checkinDate, checkoutDate);
            model.addAttribute("days", durationDays);

            return "Spamanager/bookings-details";
        } catch (EntityNotFoundException e) {
            log.error("No booking found with the provided ID", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Booking not found. Please try again later.");
            return "redirect:/manager/dashboard";
        } catch (Exception e) {
            log.error("An error occurred while displaying booking details", e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred. Please try again later.");
            return "redirect:/manager/dashboard";
        }
    }

    private Long getCurrentManagerId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.findUserByUsername(username).getSpaManager().getId();
    }
}
