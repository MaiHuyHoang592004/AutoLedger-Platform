package com.group1.car_rental.controller;

import com.group1.car_rental.dto.CarListingsDto;
import com.group1.car_rental.dto.CarListingsForm;
import com.group1.car_rental.dto.CarsDto;
import com.group1.car_rental.dto.CarsForm;
import com.group1.car_rental.service.CarsService;
import com.group1.car_rental.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/cars")
public class CarManagementController {

    private static final Logger logger = LoggerFactory.getLogger(CarManagementController.class);

    private final CarsService carsService;
    private final UserService userService;

    private static final String UPLOAD_DIR = "src/main/resources/static/uploads/";

    public CarManagementController(CarsService carsService, UserService userService) {
        this.carsService = carsService;
        this.userService = userService;
    }

    @GetMapping("/list")
    public String showCarList(Model model) {
        try {
            Long ownerId = getCurrentUserId();
            List<CarsDto> vehicles = carsService.getVehiclesByOwner(ownerId);
            model.addAttribute("vehicles", vehicles);
            return "cars/list_car";
        } catch (IllegalArgumentException e) {
            logger.error("Error retrieving car list for user: {}", e.getMessage());
            model.addAttribute("error", "Không thể tải danh sách xe: " + e.getMessage());
            return "error";
        }
    }

    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("carForm", new CarsForm());
        model.addAttribute("action", "add");
        return "cars/add";
    }

    @PostMapping("/add")
    public String addCar(@Valid @ModelAttribute("carForm") CarsForm form,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        if (bindingResult.hasErrors()) {
            logger.warn("Validation errors during car creation: {}", bindingResult.getAllErrors());
            model.addAttribute("action", "add");
            return "cars/add";
        }
        try {
            // Additional manual check to enforce plateMasked and images (though validation should catch it)
            if (form.getPlateMasked() == null || form.getPlateMasked().trim().isEmpty()) {
                bindingResult.rejectValue("plateMasked", "error.plateMasked", "Biển số không được để trống");
                model.addAttribute("action", "add");
                return "cars/add";
            }
            if (form.getImageFiles() == null || form.getImageFiles().isEmpty() || form.getImageFiles().stream().allMatch(MultipartFile::isEmpty)) {
                bindingResult.rejectValue("imageFiles", "error.imageFiles", "Vui lòng tải lên ít nhất 1 ảnh xe");
                model.addAttribute("action", "add");
                return "cars/add";
            }

            List<String> newImageUrls = new ArrayList<>();
            if (form.getImageFiles() != null && !form.getImageFiles().isEmpty()) {
                for (MultipartFile file : form.getImageFiles()) {
                    if (!file.isEmpty()) {
                        String url = handleFileUpload(file);
                        if (url != null) {
                            newImageUrls.add(url);
                        }
                    }
                }
            }

            if (newImageUrls.size() > 5) {
                model.addAttribute("error", "Tối đa 5 ảnh!");
                model.addAttribute("action", "add");
                return "cars/add";
            }

            form.setImageUrls(newImageUrls); // ← OK

            if (form.getVinString() != null && !form.getVinString().isEmpty()) {
                form.setVinEncrypted(form.getVinString().getBytes(StandardCharsets.UTF_8));
            }

            Long ownerId = getCurrentUserId();
            carsService.createVehicle(form, ownerId);
            redirectAttributes.addFlashAttribute("success", "Thêm xe thành công!");
            return "redirect:/cars/list";
        } catch (Exception e) {
            logger.error("Error creating car: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("action", "add");
            return "cars/add";
        }
    }


    @GetMapping("/edit/{id}")
public String showEditForm(@PathVariable Long id, Model model) {
    try {
        Long ownerId = getCurrentUserId();
        CarsDto car = carsService.getVehicleByIdAndOwner(id, ownerId);
        CarsForm form = new CarsForm();
        form.setId(car.getId());
        form.setMake(car.getMake());
        form.setModel(car.getModel());
        form.setYear(car.getYear());
        form.setTransmission(car.getTransmission());
        form.setFuelType(car.getFuelType());
        form.setSeats(car.getSeats());
        form.setDailyPrice(car.getDailyPrice());
        form.setCity(car.getCity());
        form.setPlateMasked(car.getPlateMasked());
        form.setExistingImageUrls(car.getImageUrls() != null ? car.getImageUrls() : new ArrayList<>()); // ← ĐẢM BẢO KHÔNG NULL
        if (car.getVinEncrypted() != null) {
            form.setVinString(new String(car.getVinEncrypted(), StandardCharsets.UTF_8));
        }
        model.addAttribute("carForm", form);
        model.addAttribute("action", "edit");
        return "cars/add";
    } catch (IllegalArgumentException e) {
        logger.error("Error retrieving car for edit: {}", e.getMessage());
        model.addAttribute("error", e.getMessage());
        model.addAttribute("carForm", new CarsForm());
        model.addAttribute("action", "edit");
        return "cars/add";
    }
}


    @PostMapping("/edit/{id}")
public String updateCar(@PathVariable Long id,
                        @Valid @ModelAttribute("carForm") CarsForm form,
                        BindingResult bindingResult,
                        RedirectAttributes redirectAttributes,
                        Model model) throws Exception {
    if (bindingResult.hasErrors()) {
        logger.warn("Validation errors during car update: {}", bindingResult.getAllErrors());
        model.addAttribute("action", "edit");
        return "cars/add";
    }
    try {
        // === XỬ LÝ ẢNH MỚI (MULTIPLE) ===
        List<String> newImageUrls = new ArrayList<>();
        if (form.getImageFiles() != null && !form.getImageFiles().isEmpty()) {
            for (MultipartFile file : form.getImageFiles()) {
                if (!file.isEmpty()) {
                    String url = handleFileUpload(file);
                    if (url != null) {
                        newImageUrls.add(url);
                    }
                }
            }
        }

        // GỘP: existing (cũ) + new (mới)
        List<String> finalUrls = new ArrayList<>(form.getExistingImageUrls() != null ? form.getExistingImageUrls() : new ArrayList<>());
        finalUrls.addAll(newImageUrls);

        // ĐẢM BẢO: Luôn có ít nhất 1 ảnh
        if (finalUrls.isEmpty()) {
            model.addAttribute("error", "Xe phải có ít nhất 1 ảnh!");
            model.addAttribute("action", "edit");
            return "cars/add";
        }

        if (finalUrls.size() > 5) {
            model.addAttribute("error", "Tối đa 5 ảnh!");
            model.addAttribute("action", "edit");
            return "cars/add";
        }

        form.setImageUrls(finalUrls); // ← ĐÚNG CÚ PHÁP

        // === VIN ===
        if (form.getVinString() != null && !form.getVinString().isEmpty()) {
            form.setVinEncrypted(form.getVinString().getBytes(StandardCharsets.UTF_8));
        }

        Long ownerId = getCurrentUserId();
        carsService.updateVehicle(id, form, ownerId);
        redirectAttributes.addFlashAttribute("success", "Cập nhật xe thành công!");
        return "redirect:/cars/list";
    } catch (Exception e) {
        logger.error("Error updating car: {}", e.getMessage());
        model.addAttribute("error", e.getMessage());
        model.addAttribute("action", "edit");
        return "cars/add";
    }
}


    @GetMapping("/create-listing/{vehicleId}")
public String showCreateListingForm(@PathVariable Long vehicleId, Model model) {
    Long ownerId = getCurrentUserId();
    CarsDto car = carsService.getVehicleByIdAndOwner(vehicleId, ownerId);
    CarListingsForm form = new CarListingsForm();
    form.setVehicleId(vehicleId);
    model.addAttribute("listingForm", form);
    model.addAttribute("car", car);
    return "cars/create_listing";
}
    @PostMapping("/create-listing")
public String createListing(@Valid @ModelAttribute("listingForm") CarListingsForm form,
                            BindingResult br, RedirectAttributes ra, Model model) {
    if (br.hasErrors()) {
        model.addAttribute("car", carsService.getVehicleById(form.getVehicleId()));
        return "cars/create_listing";
    }
    try {
        Long ownerId = getCurrentUserId();
        carsService.createCarListing(form, ownerId);
        ra.addFlashAttribute("success", "Tạo bài đăng thành công! Đang chờ duyệt.");
        return "redirect:/cars/listings";
    } catch (Exception e) {
        model.addAttribute("error", e.getMessage());
        model.addAttribute("car", carsService.getVehicleById(form.getVehicleId()));
        return "cars/create_listing";
    }
}

    @PostMapping("/delete/{id}")
    public String deleteCar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Long ownerId = getCurrentUserId();
            carsService.deleteVehicle(id, ownerId);
            redirectAttributes.addFlashAttribute("success", "Xóa xe thành công!");
        } catch (IllegalArgumentException e) {
            logger.error("Error deleting car: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cars/list";
    }

    @GetMapping("/detail/{id}")
    public String showCarDetail(@PathVariable Long id, Model model) {
        try {
            Long ownerId = getCurrentUserId();
            CarsDto car = carsService.getVehicleByIdAndOwner(id, ownerId);
            model.addAttribute("vehicle", car);
            return "cars/detail_car";
        } catch (IllegalArgumentException e) {
            logger.error("Error retrieving car detail: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
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

    private String handleFileUpload(MultipartFile file) throws Exception {
        if (file != null && !file.isEmpty()) {
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath);
        return "/uploads/" + fileName;
    }
    return null;
}
    // === DANH SÁCH BÀI ĐĂNG ===
@GetMapping("/listings")
public String showListings(Model model) {
    Long ownerId = getCurrentUserId();
    List<CarListingsDto> listings = carsService.getListingsByOwner(ownerId);
    model.addAttribute("listings", listings);
    return "cars/list_listings";
}

// === CHỈNH SỬA BÀI ĐĂNG ===
@GetMapping("/listings/edit/{id}")
public String showEditListingForm(@PathVariable Long id, Model model) {
    try {
        Long ownerId = getCurrentUserId();
        CarListingsDto listingDto = carsService.getListingByIdAndOwner(id, ownerId);
        CarsDto car = carsService.getVehicleById(listingDto.getVehicleId());

        CarListingsForm form = new CarListingsForm();
        form.setId(listingDto.getId());
        form.setVehicleId(listingDto.getVehicleId());
        form.setTitle(listingDto.getTitle());
        form.setDescription(listingDto.getDescription());
        form.setPrice24hCents(listingDto.getPrice24hCents());
        form.setKmLimit24h(listingDto.getKmLimit24h());
        form.setInstantBook(listingDto.getInstantBook());
        form.setCancellationPolicy(listingDto.getCancellationPolicy());
        form.setStatus(listingDto.getStatus());
        form.setHomeCity(listingDto.getHomeCity());
        form.setLongitude(listingDto.getLongitude());
        form.setLatitude(listingDto.getLatitude());

        model.addAttribute("listingForm", form);
        model.addAttribute("car", car);
        model.addAttribute("action", "edit");
        return "cars/edit_listing";
    } catch (IllegalArgumentException e) {
        model.addAttribute("error", e.getMessage());
        return "redirect:/cars/listings";
    }
}

@PostMapping("/listings/edit/{id}")
public String updateListing(@PathVariable Long id,
                           @Valid @ModelAttribute("listingForm") CarListingsForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes,
                           Model model) {
    if (bindingResult.hasErrors()) {
        model.addAttribute("action", "edit");
        CarsDto car = carsService.getVehicleById(form.getVehicleId());
        model.addAttribute("car", car);
        return "cars/edit_listing";
    }
    try {
        Long ownerId = getCurrentUserId();
        carsService.updateCarListing(id, form, ownerId);
        redirectAttributes.addFlashAttribute("success", "Cập nhật bài đăng thành công!");
        return "redirect:/cars/listings";
    } catch (Exception e) {
        model.addAttribute("error", e.getMessage());
        model.addAttribute("action", "edit");
        return "cars/edit_listing";
    }
}

// === XÓA BÀI ĐĂNG ===
@PostMapping("/listings/delete/{id}")
public String deleteListing(@PathVariable Long id, RedirectAttributes redirectAttributes) {
    try {
        Long ownerId = getCurrentUserId();
        carsService.deleteCarListing(id, ownerId);
        redirectAttributes.addFlashAttribute("success", "Xóa bài đăng thành công!");
    } catch (IllegalArgumentException e) {
        redirectAttributes.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/cars/listings";
}
}
