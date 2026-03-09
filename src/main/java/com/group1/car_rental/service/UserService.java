package com.group1.car_rental.service;

import com.group1.car_rental.entity.User;
import com.group1.car_rental.entity.UserProfile;
import com.group1.car_rental.entity.HostStatus;
import com.group1.car_rental.repository.UserProfileRepository;
import com.group1.car_rental.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository profileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public User register(String email, String fullName, String rawPassword, String phone, String role) {
        logger.debug("Starting user registration for email: {}", email);

        // Validate inputs
        if (email == null || !email.contains("@") || email.length() > 190) {
            logger.warn("Invalid email format: {}", email);
            throw new IllegalArgumentException("Email không hợp lệ");
        }
        if (fullName == null || fullName.trim().isEmpty() || fullName.length() > 120) {
            logger.warn("Invalid full name: {}", fullName);
            throw new IllegalArgumentException("Tên đầy đủ không hợp lệ");
        }
        if (phone != null && !phone.matches("^((\\+84|0)[35789]\\d{8}|(\\d{4}\\s\\d{3}\\s\\d{3})|(\\d{2}\\s\\d{3}\\s\\d{3}\\s\\d{3}))?$")) {
            logger.warn("Invalid phone format: {}", phone);
            throw new IllegalArgumentException("Số điện thoại không hợp lệ");
        }

        // Check duplicate email
        logger.debug("Checking for duplicate email: {}", email);
        if (userRepository.findByEmail(email).isPresent()) {
            logger.warn("Duplicate email found: {}", email);
            throw new DuplicateEmailException("Email đã tồn tại");
        }

        // Encode password
        logger.debug("Encoding password for user: {}", email);
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // Create and save user - ALWAYS CUSTOMER, ignore role parameter
        logger.debug("Creating user entity for: {}", email);
        User user = new User(email, encodedPassword, "CUSTOMER"); // Always CUSTOMER
        if (phone != null) {
            user.setPhone(phone.trim());
        }
        user.setUpdatedAt(java.time.Instant.now());
        User savedUser = userRepository.save(user);
        logger.debug("User saved with ID: {}", savedUser.getId());

        // Create and save profile
        logger.debug("Creating user profile for user ID: {}", savedUser.getId());
        UserProfile profile = new UserProfile(fullName.trim(), savedUser);
        profileRepository.save(profile);

        logger.info("User registration completed successfully for email: {}", email);
        return savedUser;
    }

    @Transactional
    public User registerWithHostRequest(String email, String fullName, String rawPassword, String phone, boolean wantToBeHost) {
        logger.debug("Starting user registration with host request for email: {}", email);

        // Validate inputs
        if (email == null || !email.contains("@") || email.length() > 190) {
            logger.warn("Invalid email format: {}", email);
            throw new IllegalArgumentException("Email không hợp lệ");
        }
        if (fullName == null || fullName.trim().isEmpty() || fullName.length() > 120) {
            logger.warn("Invalid full name: {}", fullName);
            throw new IllegalArgumentException("Tên đầy đủ không hợp lệ");
        }
        if (phone != null && !phone.matches("^((\\+84|0)[35789]\\d{8}|(\\d{4}\\s\\d{3}\\s\\d{3})|(\\d{2}\\s\\d{3}\\s\\d{3}\\s\\d{3}))?$")) {
            logger.warn("Invalid phone format: {}", phone);
            throw new IllegalArgumentException("Số điện thoại không hợp lệ");
        }

        // Check duplicate email
        logger.debug("Checking for duplicate email: {}", email);
        if (userRepository.findByEmail(email).isPresent()) {
            logger.warn("Duplicate email found: {}", email);
            throw new DuplicateEmailException("Email đã tồn tại");
        }

        // Encode password
        logger.debug("Encoding password for user: {}", email);
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // Create and save user - ALWAYS CUSTOMER
        logger.debug("Creating user entity for: {}", email);
        User user = new User(email, encodedPassword, "CUSTOMER");
        if (phone != null) {
            user.setPhone(phone.trim());
        }

        // Set host status based on checkbox
        if (wantToBeHost) {
            user.setHostStatus(HostStatus.PENDING);
        } else {
            user.setHostStatus(HostStatus.NONE);
        }

        user.setUpdatedAt(java.time.Instant.now());
        User savedUser = userRepository.save(user);
        logger.debug("User saved with ID: {}", savedUser.getId());

        // Create and save profile
        logger.debug("Creating user profile for user ID: {}", savedUser.getId());
        UserProfile profile = new UserProfile(fullName.trim(), savedUser);
        profileRepository.save(profile);

        logger.info("User registration completed successfully for email: {} (host request: {})", email, wantToBeHost);
        return savedUser;
    }

    @Transactional(readOnly = true)
    public User getCurrentUserWithProfile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserDetails)) {
            throw new IllegalStateException("User not authenticated");
        }
        String email = ((UserDetails) auth.getPrincipal()).getUsername();
        return userRepository.findByEmailWithProfile(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @Transactional
    public void updateProfile(String fullName, String phone) {
        // Validate
        if (fullName == null || fullName.trim().isEmpty() || fullName.length() > 120) {
            throw new IllegalArgumentException("Tên đầy đủ không hợp lệ");
        }
        if (phone != null && phone.length() > 30) {
            throw new IllegalArgumentException("Số điện thoại không hợp lệ");
        }

        User user = getCurrentUserWithProfile();
        user.getProfile().setFullName(fullName.trim());
        if (phone != null) {
            user.setPhone(phone.trim());
        }
        user.setUpdatedAt(java.time.Instant.now());
        user.getProfile().setUpdatedAt(java.time.Instant.now());
        userRepository.save(user);
    }

    // Dán phương thức này vào file UserService.java của bạn

@Transactional
public void changePassword(String oldPassword, String newPassword) {
    // 1. Lấy user hiện tại
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String email = ((UserDetails) auth.getPrincipal()).getUsername();
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

    // 2. Kiểm tra mật khẩu cũ
    if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
        throw new IllegalArgumentException("Mật khẩu cũ không chính xác.");
    }

    // 3. Mã hóa và lưu mật khẩu mới
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.setUpdatedAt(java.time.Instant.now());
    userRepository.save(user);
    
    logger.info("Đổi mật khẩu thành công cho user: {}", email);
}

    @Transactional
    public void saveUser(User user) {
        userRepository.save(user);
    }

    public static class DuplicateEmailException extends RuntimeException {
        public DuplicateEmailException(String message) {
            super(message);
        }
    }
}
