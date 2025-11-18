package com.nirvana.application.controller;

import com.nirvana.application.model.dto.SpaAvailabilityDTO;
import com.nirvana.application.model.dto.SpaSearchDTO;
import com.nirvana.application.service.SpaSearchService;
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
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class SpaSearchController {

    private final SpaSearchService SpaSearchService;

    @GetMapping("/search")
    public String showSearchForm(@ModelAttribute("SpaSearchDTO") SpaSearchDTO SpaSearchDTO) {
        return "Spasearch/search";
    }


    @PostMapping("/search")
    public String findAvailableSpasByCityAndDate(@Valid @ModelAttribute("SpaSearchDTO") SpaSearchDTO SpaSearchDTO, BindingResult result) {
        if (result.hasErrors()) {
            return "Spasearch/search";
        }
        try {
            validateCheckinAndCheckoutDates(SpaSearchDTO.getCheckinDate(), SpaSearchDTO.getCheckoutDate());
        } catch (IllegalArgumentException e) {
            result.rejectValue("checkoutDate", null, e.getMessage());
            return "Spasearch/search";
        }

        // Redirect to a new GET endpoint with parameters for data fetching. Allows page refreshing
        return String.format("redirect:/search-results?city=%s&checkinDate=%s&checkoutDate=%s", SpaSearchDTO.getCity(), SpaSearchDTO.getCheckinDate(), SpaSearchDTO.getCheckoutDate());
    }

    @GetMapping("/search-results")
    public String showSearchResults(@RequestParam String city, @RequestParam String checkinDate, @RequestParam String checkoutDate, Model model, RedirectAttributes redirectAttributes) {
        try {
            LocalDate parsedCheckinDate = LocalDate.parse(checkinDate);
            LocalDate parsedCheckoutDate = LocalDate.parse(checkoutDate);

            validateCheckinAndCheckoutDates(parsedCheckinDate, parsedCheckoutDate);

            log.info("Searching Spas for city {} between dates {} and {}", city, checkinDate, checkoutDate);
            List<SpaAvailabilityDTO> Spas = SpaSearchService.findAvailableSpasByCityAndDate(city, parsedCheckinDate, parsedCheckoutDate);

            if (Spas.isEmpty()) {
                model.addAttribute("noSpasFound", true);
            }

            long durationDays = ChronoUnit.DAYS.between(parsedCheckinDate, parsedCheckoutDate);

            model.addAttribute("Spas", Spas);
            model.addAttribute("city", city);
            model.addAttribute("days", durationDays);
            model.addAttribute("checkinDate", checkinDate);
            model.addAttribute("checkoutDate", checkoutDate);

        } catch (DateTimeParseException e) {
            log.error("Invalid date format provided for URL search", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid date format. Please use the search form.");
            return "redirect:/search";
        } catch (IllegalArgumentException e) {
            log.error("Invalid arguments provided for URL search", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/search";
        } catch (Exception e) {
            log.error("An error occurred while searching for Spas", e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred. Please try again later.");
            return "redirect:/search";
        }

        return "Spasearch/search-results";
    }

    @GetMapping("/Spa-details/{id}")
    public String showSpaDetails(@PathVariable Long id, @RequestParam String checkinDate, @RequestParam String checkoutDate, Model model, RedirectAttributes redirectAttributes) {
        try {
            LocalDate parsedCheckinDate = LocalDate.parse(checkinDate);
            LocalDate parsedCheckoutDate = LocalDate.parse(checkoutDate);

            validateCheckinAndCheckoutDates(parsedCheckinDate, parsedCheckoutDate);

            SpaAvailabilityDTO SpaAvailabilityDTO = SpaSearchService.findAvailableSpaById(id, parsedCheckinDate, parsedCheckoutDate);

            long durationDays = ChronoUnit.DAYS.between(parsedCheckinDate, parsedCheckoutDate);

            model.addAttribute("Spa", SpaAvailabilityDTO);
            model.addAttribute("durationDays", durationDays);
            model.addAttribute("checkinDate", checkinDate);
            model.addAttribute("checkoutDate", checkoutDate);

            return "Spasearch/Spa-details";


        } catch (DateTimeParseException e) {
            log.error("Invalid date format provided", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid date format. Please use the search form.");
            return "redirect:/search";
        } catch (IllegalArgumentException e) {
            log.error("Invalid arguments provided for URL search", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/search";
        } catch (EntityNotFoundException e) {
            log.error("No Spa found with ID {}", id);
            redirectAttributes.addFlashAttribute("errorMessage", "The selected Spa is no longer available. Please start a new search.");
            return "redirect:/search";
        } catch (Exception e) {
            log.error("An error occurred while searching for Spas", e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred. Please try again later.");
            return "redirect:/search";
        }
    }

    private void validateCheckinAndCheckoutDates(LocalDate checkinDate, LocalDate checkoutDate) {
        if (checkinDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Check-in date cannot be in the past");
        }
        if (checkoutDate.isBefore(checkinDate.plusDays(1))) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }
    }

    private void parseAndValidateBookingDates(String checkinDate, String checkoutDate) {
        LocalDate parsedCheckinDate = LocalDate.parse(checkinDate);
        LocalDate parsedCheckoutDate = LocalDate.parse(checkoutDate);
        validateCheckinAndCheckoutDates(parsedCheckinDate, parsedCheckoutDate);
    }

}