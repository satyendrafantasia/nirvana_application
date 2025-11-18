package com.nirvana.application.controller;

import com.nirvana.application.exception.SpaAlreadyExistsException;
import com.nirvana.application.exception.UsernameAlreadyExistsException;
import com.nirvana.application.model.dto.BookingDTO;
import com.nirvana.application.model.dto.SpaDTO;
import com.nirvana.application.model.dto.UserDTO;
import com.nirvana.application.service.BookingService;
import com.nirvana.application.service.SpaService;
import com.nirvana.application.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final UserService userService;
    private final SpaService SpaService;
    private final BookingService bookingService;

    @GetMapping("/dashboard")
    public String dashboard() {
        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String listUsers(Model model) {
        List<UserDTO> userDTOList = userService.findAllUsers();
        model.addAttribute("users", userDTOList);
        return "admin/users";
    }

    @GetMapping("/users/edit/{id}")
    public String showEditUserForm(@PathVariable Long id, Model model) {
        UserDTO userDTO = userService.findUserById(id);
        model.addAttribute("user", userDTO);
        return "admin/users-edit";
    }

    @PostMapping("/users/edit/{id}")
    public String editUser(@PathVariable Long id, @Valid @ModelAttribute("user") UserDTO userDTO, BindingResult result, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "admin/users-edit";
        }
        try {
            userService.updateUser(userDTO);
        } catch (UsernameAlreadyExistsException e) {
            result.rejectValue("username", "user.exists", "Username is already registered!");
            return "admin/users-edit";
        }

        redirectAttributes.addFlashAttribute("updatedUserId", userDTO.getId());
        return "redirect:/admin/users?success";
    }

    // Workaround for @DeleteMapping via post method
    @PostMapping("/users/delete/{id}")
    public String deleteUser(@PathVariable Long id) {
        userService.deleteUserById(id);
        return "redirect:/admin/users";
    }

    @GetMapping("/Spas")
    public String listSpas(Model model) {
        List<SpaDTO> SpaDTOList = SpaService.findAllSpas();
        model.addAttribute("Spas", SpaDTOList);
        return "admin/Spas";
    }

    @GetMapping("/Spas/edit/{id}")
    public String showEditSpaForm(@PathVariable Long id, Model model) {
        SpaDTO SpaDTO = SpaService.findSpaDtoById(id);
        model.addAttribute("Spa", SpaDTO);
        return "admin/Spas-edit";
    }

    @PostMapping("/Spas/edit/{id}")
    public String editSpa(@PathVariable Long id, @Valid @ModelAttribute("Spa") SpaDTO SpaDTO, BindingResult result, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "admin/Spas-edit";
        }
        try {
            SpaService.updateSpa(SpaDTO);
        } catch (SpaAlreadyExistsException e) {
            result.rejectValue("name", "Spa.exists", e.getMessage());
            return "admin/Spas-edit";
        }

        redirectAttributes.addFlashAttribute("updatedSpaId", SpaDTO.getId());
        return "redirect:/admin/Spas?success";
    }

    @PostMapping("/Spas/delete/{id}")
    public String deleteSpa(@PathVariable Long id) {
        SpaService.deleteSpaById(id);
        return "redirect:/admin/Spas";
    }

    @GetMapping("/bookings")
    public String listBookings(Model model) {
        List<BookingDTO> bookingDTOList = bookingService.findAllBookings();
        model.addAttribute("bookings", bookingDTOList);
        return "admin/bookings";
    }

    @GetMapping("/bookings/{id}")
    public String viewBookingDetails(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            BookingDTO bookingDTO = bookingService.findBookingById(id);
            model.addAttribute("bookingDTO", bookingDTO);

            LocalDate checkinDate = bookingDTO.getCheckinDate();
            LocalDate checkoutDate = bookingDTO.getCheckoutDate();
            long durationDays = ChronoUnit.DAYS.between(checkinDate, checkoutDate);
            model.addAttribute("days", durationDays);

            return "admin/bookings-details";
        } catch (EntityNotFoundException e) {
            log.error("No booking found with the provided ID", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Booking not found. Please try again later.");
            return "redirect:/admin/dashboard";
        } catch (Exception e) {
            log.error("An error occurred while displaying booking details", e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred. Please try again later.");
            return "redirect:/admin/dashboard";
        }
    }

}
