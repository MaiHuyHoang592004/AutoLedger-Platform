package com.group1.car_rental.service;

import com.group1.car_rental.dto.CarListingsDto;
import com.group1.car_rental.dto.CarListingsForm;
import com.group1.car_rental.dto.CarsDto;
import com.group1.car_rental.dto.CarsForm;
import com.group1.car_rental.entity.AvailabilityCalendar;
import com.group1.car_rental.entity.CarListings;
import com.group1.car_rental.entity.Cars;
import com.group1.car_rental.entity.User;
import com.group1.car_rental.mapper.CarsMapper;
import com.group1.car_rental.repository.CarsRepository;
import com.group1.car_rental.repository.UserRepository;
import com.group1.car_rental.repository.AvailabilityCalendarRepository;
import com.group1.car_rental.repository.CarListingsRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;




@Service
@RequiredArgsConstructor
public class CarsServiceImpl implements CarsService {

    private final CarsRepository vehicleRepository;
    private final CarsMapper vehicleMapper;
    private final UserRepository userRepository;
    private final CarListingsRepository carListingsRepository;
    private final AvailabilityCalendarRepository availabilityCalendarRepository;

    // XÓA HOÀN TOÀN GeometryFactory (không cần nữa)
    // private final GeometryFactory geometryFactory = ...

    @Override
    public List<CarsDto> getAllVehicles() {
        List<Cars> cars = vehicleRepository.findAll();
        return vehicleMapper.toDtoList(cars);
    }

    @Override
    public List<CarsDto> searchByLocation(String location) {
        if (location == null || location.trim().isEmpty()) {
            return getAllVehicles();
        }
        List<Cars> cars = vehicleRepository.searchByLocation(location.trim());
        return vehicleMapper.toDtoList(cars);
    }

    @Override
    public List<CarsDto> searchVehicles(String location, LocalDate startDate, LocalDate endDate, Double maxPrice, String fuelType, Integer seats) {
        List<Cars> cars = vehicleRepository.searchByLocation(location != null ? location.trim() : "");
        return vehicleMapper.toDtoList(cars.stream()
                .filter(car -> maxPrice == null || car.getDailyPrice() <= maxPrice)
                .filter(car -> fuelType == null || car.getFuelType().equalsIgnoreCase(fuelType))
                .filter(car -> seats == null || car.getSeats() >= seats)
                .collect(Collectors.toList()));
    }

    @Override
    public List<CarsDto> getVehiclesByOwner(Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        List<Cars> cars = vehicleRepository.findByOwner(owner);
        return vehicleMapper.toDtoList(cars);
    }

    @Override
    public CarsDto getVehicleByIdAndOwner(Long id, Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Cars car = vehicleRepository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new IllegalArgumentException("Car not found or not owned by user"));
        return vehicleMapper.toDto(car);
    }

    @Override
    public CarsDto getVehicleById(Long id) {
        Cars car = vehicleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        return vehicleMapper.toDto(car);
    }

    @Override
    @Transactional
    public CarsDto createVehicle(CarsForm form, Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Cars car = new Cars();
        car.setOwner(owner);
        car.setMake(form.getMake());
        car.setModel(form.getModel());
        car.setYear(form.getYear());
        car.setTransmission(form.getTransmission());
        car.setFuelType(form.getFuelType());
        car.setSeats(form.getSeats());
        car.setDailyPrice(form.getDailyPrice());
        car.setImageUrls(form.getImageUrls());
        car.setCity(form.getCity());
        car.setVinEncrypted(form.getVinEncrypted());
        car.setPlateMasked(form.getPlateMasked());
        car.setCreatedAt(Instant.now());
        car.setUpdatedAt(Instant.now());
        car.setStatus("ACTIVE");
        Cars savedCar = vehicleRepository.save(car);
        return vehicleMapper.toDto(savedCar);
    }

    @Override
    @Transactional
    public CarsDto updateVehicle(Long id, CarsForm form, Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Cars car = vehicleRepository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new IllegalArgumentException("Car not found or not owned by user"));
        car.setMake(form.getMake());
        car.setModel(form.getModel());
        car.setYear(form.getYear());
        car.setTransmission(form.getTransmission());
        car.setFuelType(form.getFuelType());
        car.setSeats(form.getSeats());
        car.setDailyPrice(form.getDailyPrice());
        car.setImageUrls(form.getImageUrls());
        car.setCity(form.getCity());
        car.setVinEncrypted(form.getVinEncrypted());
        car.setPlateMasked(form.getPlateMasked());
        car.setUpdatedAt(Instant.now());
        Cars updatedCar = vehicleRepository.save(car);
        return vehicleMapper.toDto(updatedCar);
    }

    @Override
    @Transactional
    public void deleteVehicle(Long id, Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Cars car = vehicleRepository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new IllegalArgumentException("Car not found or not owned by user"));
        vehicleRepository.delete(car);
    }



@Override
@Transactional
public CarListingsDto createCarListing(CarListingsForm form, Long ownerId) {
    User owner = userRepository.findById(ownerId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    Cars car = vehicleRepository.findByIdAndOwner(form.getVehicleId(), owner)
            .orElseThrow(() -> new IllegalArgumentException("Car not found or not owned by user"));

    if (carListingsRepository.existsByVehicleIdAndStatus(form.getVehicleId(), CarListings.ListingStatus.ACTIVE)) {
        throw new IllegalArgumentException("Car already has an active listing");
    }

    if (form.getLongitude() == null || form.getLatitude() == null) {
        throw new IllegalArgumentException("Tọa độ không hợp lệ");
    }
    

    // TẠO WKT
    String wkt = String.format("POINT(%.6f %.6f)", form.getLongitude(), form.getLatitude());

    // TẠO LISTING
    CarListings listing = new CarListings();
    listing.setVehicle(car);
    listing.setTitle(form.getTitle());
    listing.setDescription(form.getDescription());
    listing.setPrice24hCents(form.getPrice24hCents());
    listing.setKmLimit24h(form.getKmLimit24h());
    listing.setInstantBook(form.getInstantBook());
    listing.setCancellationPolicy(CarListings.CancellationPolicy.valueOf(form.getCancellationPolicy()));
    listing.setStatus(CarListings.ListingStatus.PENDING_REVIEW);
    listing.setHomeLocation(wkt);
    listing.setHomeCity(form.getHomeCity());
    listing.setCreatedAt(Instant.now());
    listing.setUpdatedAt(Instant.now());

    CarListings savedListing = carListingsRepository.save(listing);

    // TỰ ĐỘNG TẠO 365 NGÀY KHẢ DỤNG
    createAvailabilitySlots(savedListing.getId());

    return mapToCarListingsDto(savedListing);
}
/**
 * Tạo 365 ngày khả dụng từ hôm nay cho listing
 */
private void createAvailabilitySlots(Long listingId) {
    LocalDate startDate = LocalDate.now();
    List<AvailabilityCalendar> slots = new ArrayList<>();

    for (int i = 0; i < 365; i++) {
        LocalDate day = startDate.plusDays(i);
        AvailabilityCalendar.AvailabilityCalendarId id = 
            new AvailabilityCalendar.AvailabilityCalendarId(listingId, day);

        AvailabilityCalendar slot = new AvailabilityCalendar();
        slot.setId(id);
        slot.setStatus("FREE");
        slots.add(slot);
    }

    availabilityCalendarRepository.saveAll(slots); // Batch insert
}


@Override
public CarListingsDto getListingByIdAndOwner(Long id, Long ownerId) {
    User owner = userRepository.findById(ownerId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    CarListings listing = carListingsRepository.findByIdAndVehicleOwnerId(id, ownerId)
            .orElseThrow(() -> new IllegalArgumentException("Listing not found or not owned by user"));
    return mapToCarListingsDto(listing);
}

@Override
@Transactional
public CarListingsDto updateCarListing(Long id, CarListingsForm form, Long ownerId) {
    CarListings listing = carListingsRepository.findByIdAndVehicleOwnerId(id, ownerId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hoặc không sở hữu"));

    CarListings.ListingStatus newStatus = CarListings.ListingStatus.valueOf(form.getStatus());

    // Không cho chuyển sang ACTIVE nếu chưa được duyệt
    if (listing.getStatus() != CarListings.ListingStatus.ACTIVE && newStatus == CarListings.ListingStatus.ACTIVE)
        throw new IllegalStateException("Chỉ admin mới có thể kích hoạt");

    // Không cho quay lại PENDING_REVIEW
    if (newStatus == CarListings.ListingStatus.PENDING_REVIEW)
        throw new IllegalStateException("Không thể đặt lại trạng thái chờ duyệt");

    listing.setTitle(form.getTitle());
    listing.setDescription(form.getDescription());
    listing.setPrice24hCents(form.getPrice24hCents());
    listing.setKmLimit24h(form.getKmLimit24h());
    listing.setInstantBook(form.getInstantBook());
    listing.setCancellationPolicy(CarListings.CancellationPolicy.valueOf(form.getCancellationPolicy()));
    listing.setStatus(newStatus);
    listing.setHomeCity(form.getHomeCity());
    listing.setHomeLocation(String.format("POINT(%.6f %.6f)", form.getLongitude(), form.getLatitude()));
    listing.setUpdatedAt(Instant.now());

    carListingsRepository.save(listing);
    return mapToCarListingsDto(listing);
}

// --- ADMIN DUYỆT ---
public List<CarListingsDto> getPendingListings() {
    return carListingsRepository.findByStatus(CarListings.ListingStatus.PENDING_REVIEW)
            .stream().map(this::mapToCarListingsDto).toList();
}

// CarsServiceImpl.java
// CarsServiceImpl.java
@Override
@Transactional
public void approveListing(Long listingId) {
    CarListings listing = carListingsRepository.findById(listingId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài đăng"));

    Instant now = Instant.now();
    int updated = carListingsRepository.updateListingStatus(listingId, CarListings.ListingStatus.ACTIVE, now, listing.getVersion());

    if (updated == 0) {
        throw new IllegalStateException("Bài đăng không ở trạng thái chờ duyệt hoặc đã bị thay đổi");
    }
}
@Override
@Transactional
public void rejectListing(Long listingId) {
    CarListings listing = carListingsRepository.findById(listingId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài đăng"));

    Instant now = Instant.now();
    int updated = carListingsRepository.updateListingStatus(
            listingId, 
            CarListings.ListingStatus.SUSPENDED, 
            now, 
            listing.getVersion()
    );

    if (updated == 0) {
        throw new IllegalStateException("Bài đăng không ở trạng thái chờ duyệt hoặc đã bị thay đổi");
    }
}

@Override
@Transactional
public void deleteCarListing(Long id, Long ownerId) {
    User owner = userRepository.findById(ownerId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    CarListings listing = carListingsRepository.findByIdAndVehicleOwnerId(id, ownerId)
            .orElseThrow(() -> new IllegalArgumentException("Listing not found or not owned by user"));
    carListingsRepository.delete(listing);
}
@Override
public List<CarListingsDto> getActiveListings() {
    List<CarListings> listings = carListingsRepository.findByStatus(CarListings.ListingStatus.ACTIVE);
    return listings.stream().map(this::mapToCarListingsDto).collect(Collectors.toList());
}

@Override
public List<CarListingsDto> searchListings(String location, LocalDate startDate, LocalDate endDate,
                                          Double maxPrice, String fuelType, Integer seats) {
    List<CarListings> listings;

    if (location != null && !location.trim().isEmpty()) {
        listings = carListingsRepository.findByHomeCityContainingIgnoreCaseAndStatus(
            location.trim(), CarListings.ListingStatus.ACTIVE);
    } else {
        listings = carListingsRepository.findByStatus(CarListings.ListingStatus.ACTIVE);
    }

    return listings.stream()
            .filter(l -> maxPrice == null || (l.getPrice24hCents() / 100.0) <= maxPrice)
            .filter(l -> fuelType == null || l.getVehicle().getFuelType().equalsIgnoreCase(fuelType))
            .filter(l -> seats == null || l.getVehicle().getSeats() >= seats)
            .map(this::mapToCarListingsDto)
            .collect(Collectors.toList());
}

    // Cập nhật mapToCarListingsDto để include info từ Cars và Host
    private CarListingsDto mapToCarListingsDto(CarListings listing) {
        CarListingsDto dto = new CarListingsDto();
        dto.setId(listing.getId());
        dto.setVehicleId(listing.getVehicle().getId());
        dto.setTitle(listing.getTitle());
        dto.setDescription(listing.getDescription());
        dto.setPrice24hCents(listing.getPrice24hCents());
        dto.setKmLimit24h(listing.getKmLimit24h());
        dto.setInstantBook(listing.getInstantBook());
        dto.setCancellationPolicy(listing.getCancellationPolicy().name());
        dto.setStatus(listing.getStatus().name());
        dto.setHomeCity(listing.getHomeCity());

        // === THÔNG TIN XE ===
        Cars vehicle = listing.getVehicle();
        if (vehicle != null) {
            dto.setMake(vehicle.getMake());
            dto.setModel(vehicle.getModel());
            dto.setYear(vehicle.getYear());
            dto.setImageUrls(vehicle.getImageUrls() != null ? vehicle.getImageUrls() : new ArrayList<>());
            dto.setDailyPrice(vehicle.getDailyPrice());

            // === RATING INFO ===
            dto.setRating(vehicle.getRating());
            dto.setNumReviews(vehicle.getNumReviews());

            // === THÔNG TIN HOST ===
            User host = vehicle.getOwner();
            if (host != null) {
                dto.setHostEmail(host.getEmail());

                // Lấy tên từ UserProfile nếu có
                if (host.getProfile() != null) {
                    dto.setHostName(host.getProfile().getFullName());
                } else {
                    dto.setHostName(host.getEmail()); // Fallback
                }

                // Tính rating của host (từ reviews where toUser = host)
                // Đây là simplified - trong thực tế nên cache hoặc tính trước
                dto.setHostRating(4.8); // Placeholder
                dto.setHostNumReviews(25); // Placeholder
            }
        }

        // === TỌA ĐỘ ===
        String wkt = listing.getHomeLocation();
        if (wkt != null && wkt.startsWith("POINT(")) {
            try {
                String coords = wkt.substring(6, wkt.length() - 1).trim();
                String[] parts = coords.split("\\s+");
                if (parts.length == 2) {
                    dto.setLongitude(Double.parseDouble(parts[0]));
                    dto.setLatitude(Double.parseDouble(parts[1]));
                }
            } catch (Exception e) {
                System.err.println("Lỗi parse WKT: " + wkt);
            }
        }
        return dto;
    }
@Override
public CarListingsDto getListingById(Long id) {
    CarListings listing = carListingsRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài đăng với ID: " + id));
    return mapToCarListingsDto(listing);
}
@Override
public List<CarListingsDto> getListingsByOwner(Long ownerId) {
    User owner = userRepository.findById(ownerId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    List<CarListings> listings = carListingsRepository.findByVehicleOwnerId(ownerId);
    return listings.stream()
            .map(this::mapToCarListingsDto)
            .collect(Collectors.toList());
}
@Override
@Transactional
public void deleteVehicleAsAdmin(Long id) {
    Cars car = vehicleRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy xe với ID: " + id));
    vehicleRepository.delete(car);
}
@Override
public List<Map<String, Object>> getPendingListingsForAdmin() {
    return carListingsRepository.findByStatus(CarListings.ListingStatus.PENDING_REVIEW)
        .stream()
        .map(listing -> {
            Map<String, Object> map = new HashMap<>();
            Cars vehicle = listing.getVehicle();
            User owner = vehicle != null ? vehicle.getOwner() : null;

            map.put("id", listing.getId());
            map.put("title", listing.getTitle());
            map.put("price24hCents", listing.getPrice24hCents());
            map.put("createdAt", listing.getCreatedAt());

            // Thông tin xe
            map.put("make", vehicle != null ? vehicle.getMake() : "N/A");
            map.put("model", vehicle != null ? vehicle.getModel() : "N/A");
            map.put("year", vehicle != null ? vehicle.getYear() : null);

            // Chủ xe
            map.put("ownerEmail", owner != null ? owner.getEmail() : "N/A");

            return map;
        })
        .collect(Collectors.toList());
}
@Override
public Map<String, Object> getListingDetailForAdmin(Long id) {
    CarListings listing = carListingsRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài đăng ID: " + id));

    Cars vehicle = listing.getVehicle();
    User owner = vehicle != null ? vehicle.getOwner() : null;

    Map<String, Object> map = new HashMap<>();
    map.put("id", listing.getId());
    map.put("title", listing.getTitle());
    map.put("description", listing.getDescription());
    map.put("price24hCents", listing.getPrice24hCents());
    map.put("kmLimit24h", listing.getKmLimit24h());
    map.put("instantBook", listing.getInstantBook());
    map.put("cancellationPolicy", listing.getCancellationPolicy().name());
    map.put("status", listing.getStatus().name());
    map.put("homeCity", listing.getHomeCity());
    map.put("createdAt", listing.getCreatedAt());

    // Tọa độ
    String wkt = listing.getHomeLocation();
    if (wkt != null && wkt.startsWith("POINT(")) {
        try {
            String coords = wkt.substring(6, wkt.length() - 1).trim();
            String[] parts = coords.split("\\s+");
            map.put("longitude", Double.parseDouble(parts[0]));
            map.put("latitude", Double.parseDouble(parts[1]));
        } catch (Exception e) { /* ignore */ }
    }

    // Xe
    if (vehicle != null) {
        map.put("make", vehicle.getMake());
        map.put("model", vehicle.getModel());
        map.put("year", vehicle.getYear());
        map.put("imageUrls", vehicle.getImageUrls() != null ? vehicle.getImageUrls() : List.of());
    }

    // Chủ xe
    if (owner != null) {
        map.put("ownerEmail", owner.getEmail());
        // map.put("ownerName", owner.getFullName());
        map.put("ownerPhone", owner.getPhone());
    }

    return map;
}
}
