package com.group1.car_rental.service;

import com.group1.car_rental.dto.RentalForm;
import com.group1.car_rental.entity.Cars;
import com.group1.car_rental.entity.Rental;
import com.group1.car_rental.entity.User;
import com.group1.car_rental.repository.CarsRepository;
import com.group1.car_rental.repository.RentalRepository;
import com.group1.car_rental.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RentalServiceImpl implements RentalService {

    private final RentalRepository rentalRepository;
    private final CarsRepository carsRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void bookVehicle(Long carId, Long customerId, RentalForm form) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        Cars car = carsRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        // Check availability
        List<Rental> conflicts = rentalRepository.findConflictingRentals(carId, form.getStartDate(), form.getEndDate());
        if (!conflicts.isEmpty()) {
            throw new IllegalArgumentException("Xe không khả dụng trong khoảng thời gian đã chọn");
        }

        Rental rental = new Rental();
        rental.setCar(car);
        rental.setCustomer(customer);
        rental.setStartDate(form.getStartDate());
        rental.setEndDate(form.getEndDate());
        rental.setStatus("PENDING");
        rental.setCreatedAt(java.time.Instant.now());
        rental.setUpdatedAt(java.time.Instant.now());
        rentalRepository.save(rental);
    }

    @Override
    public List<Rental> getRentalsByCustomer(Long customerId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        return rentalRepository.findAll().stream()
                .filter(rental -> rental.getCustomer().getId().equals(customerId))
                .toList();
    }
}