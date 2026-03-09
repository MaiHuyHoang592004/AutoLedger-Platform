package com.group1.car_rental.service;

import com.group1.car_rental.dto.RentalForm;
import com.group1.car_rental.entity.Rental;
import java.util.List;

public interface RentalService {
    void bookVehicle(Long carId, Long customerId, RentalForm form);
    List<Rental> getRentalsByCustomer(Long customerId);
}