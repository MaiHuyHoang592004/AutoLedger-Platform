
package com.group1.car_rental.service;

import com.group1.car_rental.dto.CarListingsDto;
import com.group1.car_rental.dto.CarListingsForm;
import com.group1.car_rental.dto.CarsDto;
import com.group1.car_rental.dto.CarsForm;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface CarsService {
    List<CarsDto> getAllVehicles();
    List<CarsDto> searchByLocation(String location);
    List<CarsDto> searchVehicles(String location, LocalDate startDate, LocalDate endDate, Double maxPrice, String fuelType, Integer seats);
    List<CarsDto> getVehiclesByOwner(Long ownerId);
    // Thêm vào interface
    List<CarListingsDto> getActiveListings(); // Lấy tất cả bài đăng ACTIVE
    List<CarListingsDto> searchListings(String location, LocalDate startDate, LocalDate endDate, Double maxPrice, String fuelType, Integer seats); // Tìm kiếm bài đăng
    CarsDto getVehicleByIdAndOwner(Long id, Long ownerId);
    CarsDto getVehicleById(Long id);
    CarsDto createVehicle(CarsForm form, Long ownerId);
    CarsDto updateVehicle(Long id, CarsForm form, Long ownerId);
    void deleteVehicle(Long id, Long ownerId);
    CarListingsDto createCarListing(CarListingsForm form, Long ownerId);

    

    /**
     * Xóa xe với quyền Admin (bỏ qua kiểm tra owner)
     * @param id ID của xe cần xóa
     */
    void deleteVehicleAsAdmin(Long id);
    List<CarListingsDto> getListingsByOwner(Long ownerId);
    CarListingsDto getListingByIdAndOwner(Long id, Long ownerId);
    CarListingsDto updateCarListing(Long id, CarListingsForm form, Long ownerId);
    void deleteCarListing(Long id, Long ownerId);
    
    CarListingsDto getListingById(Long id);
    List<CarListingsDto> getPendingListings();
    void approveListing(Long listingId);
    List<Map<String, Object>> getPendingListingsForAdmin();
    Map<String, Object> getListingDetailForAdmin(Long id);
    void rejectListing(Long listingId);
    
}
