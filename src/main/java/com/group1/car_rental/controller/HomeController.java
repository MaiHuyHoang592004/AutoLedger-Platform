package com.group1.car_rental.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import com.group1.car_rental.service.CarsService;
import com.group1.car_rental.dto.CarListingsDto;


@Controller
@RequiredArgsConstructor
public class HomeController {

    private final CarsService carsService;

    @GetMapping
    public String home(Model model) {
        List<CarListingsDto> listings = carsService.getActiveListings();
        model.addAttribute("listings", listings); // Thay vì vehicles
        return "home/index";
    }
    @GetMapping("/search")
    public String search(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) LocalDate pickupDate, // Thay vì startDate
            @RequestParam(required = false) LocalDate returnDate, // Thay vì endDate
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String fuelType,
            @RequestParam(required = false) Integer seats,
            Model model) {
        // Map pickupDate -> startDate, returnDate -> endDate nếu cần
        List<CarListingsDto> listings = carsService.searchListings(location, pickupDate, returnDate, maxPrice, fuelType, seats);
        model.addAttribute("listings", listings);
        return "index"; // Hoặc trang search riêng nếu có
    }
}