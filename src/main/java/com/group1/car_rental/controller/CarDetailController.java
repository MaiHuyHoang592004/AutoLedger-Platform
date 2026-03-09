package com.group1.car_rental.controller;

import com.group1.car_rental.dto.CarListingsDto;
import com.group1.car_rental.dto.ReviewResponse;
import com.group1.car_rental.service.CarsService;
import com.group1.car_rental.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/cars")
@RequiredArgsConstructor
public class CarDetailController {

    private final CarsService carsService;
    private final ReviewService reviewService;

    @GetMapping("/{id}")
    public String showCarDetail(@PathVariable Long id, Model model) {
        CarListingsDto listing = carsService.getListingById(id);

        // Load reviews for this listing
        List<ReviewResponse> reviews = reviewService.getReviewsByListing(id);

        model.addAttribute("listing", listing);
        model.addAttribute("reviews", reviews);
        return "cars/detail";
    }
}
