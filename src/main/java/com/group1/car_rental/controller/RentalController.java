package com.group1.car_rental.controller;

import com.group1.car_rental.dto.CarsDto;
import com.group1.car_rental.dto.RentalForm;
import com.group1.car_rental.service.CarsService;
import com.group1.car_rental.service.RentalService;
import com.group1.car_rental.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/rentals")
public class RentalController {

    private static final Logger logger = LoggerFactory.getLogger(RentalController.class);

    private final RentalService rentalService;
    private final CarsService carsService;
    private final UserService userService;

    public RentalController(RentalService rentalService, CarsService carsService, UserService userService) {
        this.rentalService = rentalService;
        this.carsService = carsService;
        this.userService = userService;
    }

    @GetMapping("/book/{id}")
    public String showRentalForm(@PathVariable Long id, Model model) {
        try {
            CarsDto car = carsService.getVehicleById(id);
            model.addAttribute("rentalForm", new RentalForm());
            model.addAttribute("car", car);
            return "rentals/book";
        } catch (IllegalArgumentException e) {
            logger.error("Error retrieving car for booking: {}", e.getMessage());
            model.addAttribute("error", "Không tìm thấy xe: " + e.getMessage());
            return "error";
        }
    }

    @PostMapping("/book/{id}")
    public String bookCar(@PathVariable Long id,
                          @Valid @ModelAttribute("rentalForm") RentalForm form,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes,
                          Model model) {
        if (bindingResult.hasErrors()) {
            logger.warn("Validation errors during car booking: {}", bindingResult.getAllErrors());
            model.addAttribute("car", carsService.getVehicleById(id));
            return "rentals/book";
        }
        try {
            Long customerId = getCurrentUserId();
            rentalService.bookVehicle(id, customerId, form);
            redirectAttributes.addFlashAttribute("success", "Đặt xe thành công! Đang chờ xác nhận.");
            return "redirect:/rentals/status";
        } catch (IllegalArgumentException e) {
            logger.error("Error booking car: {}", e.getMessage());
            model.addAttribute("error", "Lỗi khi đặt xe: " + e.getMessage());
            model.addAttribute("car", carsService.getVehicleById(id));
            return "rentals/book";
        }
    }

    @GetMapping("/status")
    public String showRentalStatus(Model model) {
        try {
            Long customerId = getCurrentUserId();
            model.addAttribute("rentals", rentalService.getRentalsByCustomer(customerId));
            return "rentals/status";
        } catch (IllegalArgumentException e) {
            logger.error("Error retrieving rental status: {}", e.getMessage());
            model.addAttribute("error", "Không thể tải trạng thái thuê xe: " + e.getMessage());
            return "error";
        }
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("User not authenticated");
        }
        String email = ((org.springframework.security.core.userdetails.UserDetails) auth.getPrincipal()).getUsername();
        return userService.getCurrentUserWithProfile().getId();
    }
}