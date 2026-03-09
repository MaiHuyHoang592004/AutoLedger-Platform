package com.group1.car_rental.controller;

import com.group1.car_rental.entity.CarListings;
import com.group1.car_rental.entity.CityEnum;
import com.group1.car_rental.repository.CarListingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/listings")
public class ListingsController {

    @Autowired
    private CarListingsRepository carListingsRepository;

    // GET /api/listings?city=HA_NOI&startDate=2025-11-01&endDate=2025-11-05
    @GetMapping
    public ResponseEntity<List<CarListings>> searchListings(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {

        List<CarListings> listings;

        if (city != null && startDate != null && endDate != null) {
            // Search with availability filter
            CityEnum cityEnum = CityEnum.fromString(city);
            listings = carListingsRepository.findAvailableByCityAndDates(cityEnum, startDate, endDate);
        } else if (city != null) {
            // Search by city only
            CityEnum cityEnum = CityEnum.fromString(city);
            listings = carListingsRepository.findByHomeCityAndStatusOrderByPrice24hCentsAsc(cityEnum, "ACTIVE");
        } else {
            // Return empty list if no city specified
            listings = List.of();
        }

        return ResponseEntity.ok(listings);
    }

    // GET /api/listings/cities - Get available cities with counts
    @GetMapping("/cities")
    public ResponseEntity<Map<String, Object>> getCities() {
        Map<String, Object> cities = Map.of(
            "HA_NOI", Map.of(
                "name", "Hà Nội",
                "count", carListingsRepository.countByHomeCityAndStatus(CityEnum.HA_NOI, "ACTIVE")
            ),
            "HUE", Map.of(
                "name", "Huế",
                "count", carListingsRepository.countByHomeCityAndStatus(CityEnum.HUE, "ACTIVE")
            ),
            "HO_CHI_MINH", Map.of(
                "name", "Hồ Chí Minh",
                "count", carListingsRepository.countByHomeCityAndStatus(CityEnum.HO_CHI_MINH, "ACTIVE")
            ),
            "HAI_PHONG", Map.of(
                "name", "Hải Phòng",
                "count", carListingsRepository.countByHomeCityAndStatus(CityEnum.HAI_PHONG, "ACTIVE")
            )
        );

        return ResponseEntity.ok(cities);
    }
}
