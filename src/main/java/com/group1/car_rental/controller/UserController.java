package com.group1.car_rental.controller;

import com.group1.car_rental.service.UserService;
import com.group1.car_rental.entity.HostStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.group1.car_rental.entity.User;

@Controller
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("registrationForm", new RegistrationForm());
        return "user/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        logger.debug("Registration attempt for email: {}", form.getEmail());

        if (bindingResult.hasErrors()) {
            logger.warn("Validation errors during registration: {}", bindingResult.getAllErrors());
            return "user/register";
        }

        try {
            // Validate password match
            if (!form.getPassword().equals(form.getConfirmPassword())) {
                logger.warn("Password confirmation mismatch for email: {}", form.getEmail());
                redirectAttributes.addFlashAttribute("error", "Mật khẩu xác nhận không khớp");
                return "redirect:/register";
            }

            logger.debug("Calling userService.registerWithHostRequest for email: {}", form.getEmail());
            // Register user with host request
            userService.registerWithHostRequest(form.getEmail().trim(), form.getFullName().trim(),
                                               form.getPassword(), form.getPhone(), form.isWantToBeHost());

            // Always redirect to home for new CUSTOMER users
            String redirectUrl = "/";
            logger.info("Registration successful for email: {}", form.getEmail());
            redirectAttributes.addFlashAttribute("success", "Đăng ký thành công! Chào mừng bạn đến với hệ thống.");
            return "redirect:" + redirectUrl;
        } catch (UserService.DuplicateEmailException e) {
            logger.warn("Duplicate email registration attempt: {}", form.getEmail());
            redirectAttributes.addFlashAttribute("error", "Email đã tồn tại");
            return "redirect:/register";
        } catch (IllegalArgumentException e) {
            logger.warn("Validation error during registration for email {}: {}", form.getEmail(), e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        } catch (Exception e) {
            logger.error("System error during registration for email {}: {}", form.getEmail(), e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Lỗi hệ thống. Vui lòng thử lại.");
            return "redirect:/register";
        }
    }

    // Mã mới đã sửa
    @GetMapping("/login")
    public String showLoginForm(Model model, HttpServletRequest request) {
        
        // KIỂM TRA XEM ĐÃ LOGIN CHƯA
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && 
            !(authentication.getPrincipal() instanceof String && "anonymousUser".equals(authentication.getPrincipal()))) {
            
            // NẾU RỒI, ĐÁ VỀ TRANG CHỦ
            return "redirect:/";
        }

        // Nếu chưa login, hiển thị trang login
        model.addAttribute("error", request.getParameter("error"));
        return "user/login";
    }

    @GetMapping("/profile")
    public String showProfileForm(Model model) {
        User currentUser = userService.getCurrentUserWithProfile();
        model.addAttribute("user", currentUser); 

        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setFullName(currentUser.getProfile().getFullName());
        form.setPhone(currentUser.getPhone());
        
        model.addAttribute("profileForm", form);
        
        return "user/profile";
    }

    // Thêm route mới cho trang đổi mật khẩu
    @GetMapping("/change-password")
    public String showChangePasswordForm(Model model) {
        model.addAttribute("passwordChangeForm", new PasswordChangeForm());
        return "user/change-password"; // Trang mới: user/change-password.html
    }

    // Đổi route POST thành /change-password
    @PostMapping("/change-password")
    public String handleChangePassword(@Valid @ModelAttribute("passwordChangeForm") PasswordChangeForm form,
                                       BindingResult bindingResult,
                                       Model model,
                                       RedirectAttributes redirectAttributes) {

        // Nếu validation cơ bản (như @NotBlank) thất bại
        if (bindingResult.hasErrors()) {
            return "user/change-password"; // Trả về trang đổi mật khẩu với lỗi
        }

        // Kiểm tra mật khẩu xác nhận
        if (!form.getNewPassword().equals(form.getConfirmNewPassword())) {
            model.addAttribute("passwordError", "Mật khẩu mới không khớp.");
            return "user/change-password";
        }

        try {
            // Gọi service để đổi mật khẩu
            userService.changePassword(form.getOldPassword(), form.getNewPassword());
            redirectAttributes.addFlashAttribute("success", "Đổi mật khẩu thành công!");
            return "redirect:/profile";
        } catch (IllegalArgumentException e) { // Bắt lỗi (ví dụ: mật khẩu cũ sai)
            model.addAttribute("passwordError", e.getMessage());
            return "user/change-password";
        } catch (Exception e) {
            logger.error("Lỗi đổi mật khẩu: {}", e.getMessage(), e);
            model.addAttribute("passwordError", "Lỗi hệ thống, vui lòng thử lại.");
            return "user/change-password";
        }
    }

    @PostMapping("/profile/become-host")
    public String requestBecomeHost(RedirectAttributes redirectAttributes) {
        try {
            User currentUser = userService.getCurrentUserWithProfile();

            // Check if already a host
            if ("HOST".equals(currentUser.getRole())) {
                redirectAttributes.addFlashAttribute("error", "Bạn đã là host rồi!");
                return "redirect:/profile";
            }

            // Check if already pending
            if (currentUser.getHostStatus() == HostStatus.PENDING) {
                redirectAttributes.addFlashAttribute("error", "Yêu cầu làm host của bạn đang được xét duyệt!");
                return "redirect:/profile";
            }

            // Update host status to PENDING
            currentUser.setHostStatus(HostStatus.PENDING);
            userService.saveUser(currentUser); // Need to add this method to UserService

            redirectAttributes.addFlashAttribute("success", "Yêu cầu làm host đã được gửi. Vui lòng chờ admin duyệt.");
            return "redirect:/profile";
        } catch (Exception e) {
            logger.error("Error requesting to become host: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra. Vui lòng thử lại.");
            return "redirect:/profile";
        }
    }

    @PostMapping("/profile")
    public String updateProfile(@ModelAttribute("profileForm") @Valid ProfileUpdateForm form,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            // PHẢI thêm lại object 'user' vì trang profile.html cần nó
            // (Object 'profileForm' đã tự động được thêm vào model rồi)
            model.addAttribute("user", userService.getCurrentUserWithProfile());
            return "user/profile"; // Giờ thì trang render sẽ không lỗi
        }
        try {
            userService.updateProfile(form.getFullName(), form.getPhone());
            redirectAttributes.addFlashAttribute("success", "Cập nhật thành công");
            return "redirect:/profile";
        } catch (IllegalArgumentException e) {
            model.addAttribute("user", userService.getCurrentUserWithProfile()); // Thêm ở đây
            model.addAttribute("error", e.getMessage());
            return "user/profile";
        } catch (Exception e) {
            model.addAttribute("user", userService.getCurrentUserWithProfile()); // Và thêm ở đây
            model.addAttribute("error", "Lỗi hệ thống. Vui lòng thử lại.");
            return "user/profile";
        }
    }

    public static class RegistrationForm {
        @jakarta.validation.constraints.Email(message = "Email không hợp lệ")
        @jakarta.validation.constraints.NotBlank(message = "Email không được trống")
        private String email;

        @jakarta.validation.constraints.NotBlank(message = "Tên đầy đủ không được để trống")
        @jakarta.validation.constraints.Size(max = 120, message = "Tên đầy đủ quá dài")
        private String fullName;

        @jakarta.validation.constraints.NotBlank(message = "Mật khẩu không được trống")
        @jakarta.validation.constraints.Size(min = 8, max = 60, message = "Mật khẩu 8–60 ký tự")
        private String password;

        @jakarta.validation.constraints.NotBlank(message = "Xác nhận mật khẩu không được trống")
        private String confirmPassword;

        @jakarta.validation.constraints.Pattern(regexp = "^((\\+84|0)[35789]\\d{8}|(\\d{4}\\s\\d{3}\\s\\d{3})|(\\d{2}\\s\\d{3}\\s\\d{3}\\s\\d{3}))?$", message = "Số điện thoại không hợp lệ")
        private String phone;

        private boolean wantToBeHost = false;

        // Getters and setters
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public boolean isWantToBeHost() { return wantToBeHost; }
        public void setWantToBeHost(boolean wantToBeHost) { this.wantToBeHost = wantToBeHost; }

        // For backward compatibility - remove this after updating UserService
        public String getRole() { return "CUSTOMER"; }
        public void setRole(String role) { /* ignore */ }
    }

    public static class ProfileUpdateForm {
        @jakarta.validation.constraints.NotBlank(message = "Tên đầy đủ không được để trống")
        @jakarta.validation.constraints.Size(max = 120, message = "Tên đầy đủ quá dài")
        private String fullName;

        @jakarta.validation.constraints.Size(max = 30, message = "Số điện thoại quá dài")
        private String phone;
        public ProfileUpdateForm() {
        }

        public ProfileUpdateForm(String fullName, String phone) {
            this.fullName = fullName;
            this.phone = phone;
        }
        // Getters and setters
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }

    public static class PasswordChangeForm {
        @jakarta.validation.constraints.NotBlank(message = "Mật khẩu cũ không được trống")
        private String oldPassword;

        @jakarta.validation.constraints.NotBlank(message = "Mật khẩu mới không được trống")
        @jakarta.validation.constraints.Size(min = 8, max = 60, message = "Mật khẩu 8–60 ký tự")
        private String newPassword;

        @jakarta.validation.constraints.NotBlank(message = "Xác nhận mật khẩu không được trống")
        private String confirmNewPassword;

        // Getters and Setters
        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
        public String getConfirmNewPassword() { return confirmNewPassword; }
        public void setConfirmNewPassword(String confirmNewPassword) { this.confirmNewPassword = confirmNewPassword; }
    }
}
