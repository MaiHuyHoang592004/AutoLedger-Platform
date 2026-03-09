// src/main/java/com/group1/car_rental/service/CarListingsService.java
package com.group1.car_rental.service;

import com.group1.car_rental.entity.CarListings;
import com.group1.car_rental.repository.CarListingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CarListingsService {

    @Autowired
    private CarListingsRepository carListingsRepository;

    /**
     * Tìm CarListings theo ID
     * @param id ID của listing
     * @return CarListings nếu tồn tại và ACTIVE, ngược lại trả về null
     */
    public CarListings findById(Long id) {
        if (id == null) {
            return null;
        }
        return carListingsRepository.findById(id)
                .filter(listing -> "ACTIVE".equals(listing.getStatus()))
                .orElse(null);
    }

    /**
     * Tìm mà không kiểm tra status (dùng nội bộ nếu cần)
     */
    public CarListings findByIdIgnoreStatus(Long id) {
        return id != null ? carListingsRepository.findById(id).orElse(null) : null;
    }

    /**
     * Lưu hoặc cập nhật listing
     */
    public CarListings save(CarListings listing) {
        return carListingsRepository.save(listing);
    }
}