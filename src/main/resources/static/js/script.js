const tabs = document.querySelectorAll('.tab'); // Nếu có tabs
const searchForm = document.querySelector('.search-form');
const carCards = document.querySelectorAll('.car-card');
const menuBtn = document.querySelector('.menu-btn'); // Nút menu mobile
const userBtn = document.querySelector('.user-btn'); // Nút user dropdown

// Global functions for booking modal
function openBookingModal() {
    document.getElementById('bookingModal').style.display = 'flex';
    const today = new Date().toISOString().split('T')[0];
    document.getElementById('modalPickupDate').min = today;
    document.getElementById('modalReturnDate').min = today;

    // Set return date min to pickup date
    const modalPickupDateInput = document.getElementById('modalPickupDate');
    const modalReturnDateInput = document.getElementById('modalReturnDate');
    modalPickupDateInput.addEventListener('change', function() {
        if (this.value) {
            if (!modalReturnDateInput.value || modalReturnDateInput.value < this.value) {
                modalReturnDateInput.value = this.value;
            }
            modalReturnDateInput.min = this.value;
        }
    });
}

function closeBookingModal() {
    document.getElementById('bookingModal').style.display = 'none';
}

function proceedToBooking() {
    const id = document.getElementById('selectedCarId').value;
    const pickupDate = document.getElementById('modalPickupDate').value;
    const returnDate = document.getElementById('modalReturnDate').value;
    const pickupTime = document.getElementById('modalPickupTime').value;
    const returnTime = document.getElementById('modalReturnTime').value;

    if (!pickupDate || !returnDate || !pickupTime || !returnTime) {
        alert('Vui lòng chọn đầy đủ ngày và giờ nhận, trả xe.');
        return;
    }

    const button = document.querySelector('.modal-btn');
    button.disabled = true;
    button.textContent = 'Đang xử lý...';

    const url = `/bookings/create?listingId=${id}&pickupDate=${pickupDate}&returnDate=${returnDate}&pickupTime=${pickupTime}&returnTime=${returnTime}&acceptedTerms=true`;
    window.location.href = url;
}

document.addEventListener('DOMContentLoaded', () => {
    // ----------------------
    // Mobile Navigation Toggle
    // ----------------------
    const headerNav = document.querySelector('.nav'); // Menu điều hướng chính
    if (menuBtn && headerNav) {
        menuBtn.addEventListener('click', () => {
            headerNav.classList.toggle('active'); // Kích hoạt CSS để hiện/ẩn menu
            menuBtn.querySelector('i').classList.toggle('fa-bars');
            menuBtn.querySelector('i').classList.toggle('fa-times'); // Thay đổi icon X khi mở

            // Đóng user dropdown nếu nó đang mở
            const userDropdownContainer = document.querySelector('.user-dropdown-container');
            if (userDropdownContainer && userDropdownContainer.classList.contains('active')) {
                userDropdownContainer.classList.remove('active');
            }
        });

        // Đóng menu khi click vào một link (chỉ trên mobile)
        headerNav.querySelectorAll('a').forEach(link => {
            link.addEventListener('click', () => {
                if (window.innerWidth <= 768) { // Kích thước màn hình mobile
                    headerNav.classList.remove('active');
                    menuBtn.querySelector('i').classList.remove('fa-times');
                    menuBtn.querySelector('i').classList.add('fa-bars');
                }
            });
        });
    }

    // ----------------------
    // User Dropdown Toggle
    // ----------------------
    const userDropdownContainer = document.querySelector('.user-dropdown-container');
    if (userBtn && userDropdownContainer) {
        userBtn.addEventListener('click', (event) => {
            userDropdownContainer.classList.toggle('active');
            event.stopPropagation(); // Ngăn sự kiện click lan ra body

            // Đóng mobile nav nếu nó đang mở
            const headerNav = document.querySelector('.nav');
            if (headerNav && headerNav.classList.contains('active')) {
                headerNav.classList.remove('active');
                menuBtn.querySelector('i').classList.remove('fa-times');
                menuBtn.querySelector('i').classList.add('fa-bars');
            }
        });

        // Đóng dropdown khi click bên ngoài
        document.addEventListener('click', (event) => {
            if (userDropdownContainer && !userDropdownContainer.contains(event.target)) {
                userDropdownContainer.classList.remove('active');
            }
        });
    }

    // ----------------------
    // Tab Navigation (Nếu có)
    // ----------------------
    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            tabs.forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            tab.style.transform = 'scale(0.95)';
            setTimeout(() => { tab.style.transform = 'scale(1)'; }, 150);
        });
    });

    // ----------------------
    // Search Form Functionality
    // ----------------------
    if (searchForm) {
        const searchBtn = searchForm.querySelector('.search-btn');
        const inputs = searchForm.querySelectorAll('input, select');
        const locationInput = searchForm.querySelector('input#location');

        searchBtn.addEventListener('click', (e) => {
            e.preventDefault();

            // Basic validation
            let isValid = true;
            inputs.forEach(input => {
                if (input.required && !input.value) {
                    isValid = false;
                    input.classList.add('is-invalid'); // Thêm class để hiển thị lỗi CSS
                } else {
                    input.classList.remove('is-invalid');
                }
            });

            if (!isValid) {
                alert('Vui lòng điền đầy đủ thông tin tìm kiếm.');
                return;
            }

            // Add loading state
            searchBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Đang tìm...';
            searchBtn.disabled = true;

            // Submit form
            searchForm.submit();
        });

        // Auto-complete for location input
        if (locationInput) {
            const suggestions = [
                'Hà Nội, Việt Nam',
                'TP. Hồ Chí Minh, Việt Nam',
                'Đà Nẵng, Việt Nam',
                'Hải Phòng, Việt Nam',
                'Cần Thơ, Việt Nam',
                'Sân bay Nội Bài, Hà Nội',
                'Sân bay Tân Sơn Nhất, TP.HCM',
                'Sân bay Đà Nẵng',
                'Nhà ga Hà Nội',
                'Bến xe Mỹ Đình, Hà Nội'
            ];

            locationInput.addEventListener('input', (e) => {
                const value = e.target.value.toLowerCase();
                if (value.length > 2) {
                    showSuggestions(locationInput, suggestions.filter(s =>
                        s.toLowerCase().includes(value)
                    ));
                } else {
                    hideSuggestions();
                }
            });
        }
    }

    // ----------------------
    // Car Card Interactions
    // ----------------------
    carCards.forEach(card => {
        // Có thể thêm hiệu ứng hover cho giá trong CSS thay vì JS
        // Ví dụ: .car-card:hover .price-value { color: var(--primary-color); }
        // Hoặc các tương tác khác nếu cần
    });

    // ----------------------
    // Navigation Arrows for Car Sections (Carousel)
    // ----------------------
    document.querySelectorAll('.car-section').forEach(section => {
        const prevBtn = section.querySelector('.nav-arrow.prev');
        const nextBtn = section.querySelector('.nav-arrow.next');
        const carGrid = section.querySelector('.car-grid');

        if (prevBtn && nextBtn && carGrid) {
            const scrollStep = 300; // Khoảng cách cuộn mỗi lần
            let currentIndex = 0; // Vị trí cuộn hiện tại

            prevBtn.addEventListener('click', () => {
                carGrid.scrollBy({ left: -scrollStep, behavior: 'smooth' });
            });

            nextBtn.addEventListener('click', () => {
                carGrid.scrollBy({ left: scrollStep, behavior: 'smooth' });
            });

            // Tùy chọn: Ẩn/hiện mũi tên nếu không còn xe để cuộn
            carGrid.addEventListener('scroll', () => {
                // Kiểm tra khi đến đầu hoặc cuối
                // Điều này có thể phức tạp hơn với responsive, bạn có thể cần tính toán lại
            });
        }
    });


    // ----------------------
    // Date/Time Inputs setup (from your existing script)
    // ----------------------
    const today = new Date();
    const isoToday = today.toISOString().split('T')[0];

    const pickupDateInput = document.getElementById('pickupDate');
    const returnDateInput = document.getElementById('returnDate');
    const pickupTimeInput = document.getElementById('pickupTime');
    const returnTimeInput = document.getElementById('returnTime');

    if (pickupDateInput && returnDateInput) {
        pickupDateInput.min = isoToday;
        returnDateInput.min = isoToday;

        // Set default times if not already set (optional)
        if (!pickupTimeInput.value) pickupTimeInput.value = '09:00';
        if (!returnTimeInput.value) returnTimeInput.value = '18:00';

        // Ensure return date is not before pickup date
        pickupDateInput.addEventListener('change', () => {
            if (returnDateInput.value < pickupDateInput.value) {
                returnDateInput.value = pickupDateInput.value;
            }
            returnDateInput.min = pickupDateInput.value;
        });
    }

    // ----------------------
    // Utility Functions (giữ nguyên hoặc điều chỉnh nhỏ)
    // ----------------------
    function showSuggestions(input, suggestions) {
        hideSuggestions();

        if (suggestions.length === 0) return;

        const suggestionsList = document.createElement('div');
        suggestionsList.className = 'suggestions-list';
        // Style được định nghĩa trong styles.css

        suggestions.forEach(suggestion => {
            const item = document.createElement('div');
            item.className = 'suggestion-item';
            item.textContent = suggestion;

            item.addEventListener('click', () => {
                input.value = suggestion;
                hideSuggestions();
            });

            suggestionsList.appendChild(item);
        });

        // Đảm bảo phần tử cha của input có position: relative
        input.parentElement.style.position = 'relative';
        input.parentElement.appendChild(suggestionsList);
    }

    function hideSuggestions() {
        const existingSuggestions = document.querySelector('.suggestions-list');
        if (existingSuggestions) {
            existingSuggestions.remove();
        }
    }

    // Thêm các hàm validate auth form ở đây nếu chúng được sử dụng chung
    // Ví dụ: validateEmail, validatePassword, setupLoginValidation, setupRegisterValidation
    // ... (Giữ lại các hàm validateAuthForm, setupLoginValidation, setupRegisterValidation,
    //      setupPasswordToggle, setupPasswordStrength, calculatePasswordStrength,
    //      setupPhoneFormatting, showToast, setupProfileValidation)
    //      từ file script.js của bạn)
    // ----------------------
    // Auth Form Validation Functions
    // ----------------------
    function validateEmail(email) {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return emailRegex.test(email);
    }

    function validatePassword(password) {
        return password.length >= 6;
    }

    function validatePhone(phone) {
        const phoneRegex = /^((\+84|0)[35789]\d{8}|(\d{4}\s\d{3}\s\d{3})|(\d{2}\s\d{3}\s\d{3}\s\d{3}))?$/;
        return phoneRegex.test(phone) || phone === '';
    }

    function setupLoginValidation() {
        const loginForm = document.querySelector('form[action="/login"]');
        if (loginForm) {
            loginForm.addEventListener('submit', (e) => {
                const email = document.getElementById('username').value;
                const password = document.getElementById('password').value;
                if (!validateEmail(email)) {
                    e.preventDefault();
                    alert('Email không hợp lệ!');
                    return false;
                }
                if (!validatePassword(password)) {
                    e.preventDefault();
                    alert('Mật khẩu phải có ít nhất 6 ký tự!');
                    return false;
                }
                return true;
            });
        }
    }

    function setupRegisterValidation() {
        const registerForm = document.querySelector('form[action="/register"]');
        if (registerForm) {
            const password = document.getElementById('password');
            const confirmPassword = document.getElementById('confirmPassword');
            const email = document.getElementById('email');
            const fullName = document.getElementById('fullName');
            const phone = document.getElementById('phone');

            // Password toggle functionality
            setupPasswordToggle();

            // Password strength meter
            setupPasswordStrength();

            // Phone number formatting
            setupPhoneFormatting();

            // Real-time password match validation
            if (confirmPassword && password) {
                confirmPassword.addEventListener('input', () => {
                    const matchText = document.getElementById('matchText'); // Đảm bảo có thẻ này trong HTML
                    if (matchText) {
                        if (password.value !== confirmPassword.value) {
                            matchText.textContent = 'Mật khẩu không khớp!';
                            matchText.style.color = '#dc3545';
                        } else if (confirmPassword.value.length > 0) {
                            matchText.textContent = '✓ Mật khẩu khớp!';
                            matchText.style.color = '#28a745';
                        } else {
                            matchText.textContent = '';
                        }
                    }
                });
            }

            registerForm.addEventListener('submit', (e) => {
                // Validate email
                if (!validateEmail(email.value)) {
                    e.preventDefault();
                    showToast('Email không hợp lệ!', 'error');
                    email.focus();
                    return false;
                }

                // Validate full name
                if (fullName.value.trim().length < 2) {
                    e.preventDefault();
                    showToast('Tên đầy đủ phải có ít nhất 2 ký tự!', 'error');
                    fullName.focus();
                    return false;
                }

                // Validate phone if provided
                if (phone.value && !validatePhone(phone.value)) {
                    e.preventDefault();
                    showToast('Số điện thoại không hợp lệ! (Định dạng Việt Nam)', 'error');
                    phone.focus();
                    return false;
                }

                // Validate password strength
                const strength = calculatePasswordStrength(password.value);
                if (strength.score < 2) {
                    e.preventDefault();
                    showToast('Mật khẩu quá yếu! Cần ít nhất 8 ký tự với chữ hoa, chữ thường và số.', 'error');
                    password.focus();
                    return false;
                }

                // Validate password match
                if (password.value !== confirmPassword.value) {
                    e.preventDefault();
                    showToast('Mật khẩu xác nhận không khớp!', 'error');
                    confirmPassword.focus();
                    return false;
                }

                // Add timezone to form
                const timezoneInput = document.createElement('input');
                timezoneInput.type = 'hidden';
                timezoneInput.name = 'timezone';
                timezoneInput.value = Intl.DateTimeFormat().resolvedOptions().timeZone;
                registerForm.appendChild(timezoneInput);

                return true;
            });
        }
    }

    function setupPasswordToggle() {
        // Toggle password visibility
        const togglePassword = document.getElementById('togglePassword');
        const toggleConfirmPassword = document.getElementById('toggleConfirmPassword');
        const password = document.getElementById('password');
        const confirmPassword = document.getElementById('confirmPassword');

        if (togglePassword && password) {
            togglePassword.addEventListener('click', () => {
                const type = password.type === 'password' ? 'text' : 'password';
                password.type = type;
                togglePassword.querySelector('i').className = type === 'password' ? 'fas fa-eye' : 'fas fa-eye-slash';
            });
        }

        if (toggleConfirmPassword && confirmPassword) {
            toggleConfirmPassword.addEventListener('click', () => {
                const type = confirmPassword.type === 'password' ? 'text' : 'password';
                confirmPassword.type = type;
                toggleConfirmPassword.querySelector('i').className = type === 'password' ? 'fas fa-eye' : 'fas fa-eye-slash';
            });
        }
    }

    function setupPasswordStrength() {
        const password = document.getElementById('password');
        const strengthBar = document.getElementById('strengthBar'); // Đảm bảo có thẻ này trong HTML
        const strengthText = document.getElementById('strengthText'); // Đảm bảo có thẻ này trong HTML

        if (password && strengthBar && strengthText) {
            password.addEventListener('input', () => {
                const strength = calculatePasswordStrength(password.value);
                strengthBar.style.width = strength.percentage + '%';

                // Remove all classes
                strengthBar.classList.remove('bg-danger', 'bg-warning', 'bg-success');

                // Add appropriate class and text
                if (strength.score === 0) {
                    strengthBar.classList.add('bg-danger');
                    strengthText.textContent = 'Rất yếu';
                    strengthText.style.color = '#dc3545';
                } else if (strength.score === 1) {
                    strengthBar.classList.add('bg-warning');
                    strengthText.textContent = 'Yếu';
                    strengthText.style.color = '#ffc107';
                } else if (strength.score === 2) {
                    strengthBar.classList.add('bg-warning');
                    strengthText.textContent = 'Trung bình';
                    strengthText.style.color = '#ffc107';
                } else if (strength.score === 3) {
                    strengthBar.classList.add('bg-success');
                    strengthText.textContent = 'Mạnh';
                    strengthText.style.color = '#28a745';
                } else {
                    strengthBar.classList.add('bg-success');
                    strengthText.textContent = 'Rất mạnh';
                    strengthText.style.color = '#28a745';
                }
            });
        }
    }

    function calculatePasswordStrength(password) {
        let score = 0;
        let feedback = [];

        // Length check
        if (password.length >= 8) {
            score += 1;
        } else {
            feedback.push('ít nhất 8 ký tự');
        }

        // Lowercase check
        if (/[a-z]/.test(password)) {
            score += 1;
        } else {
            feedback.push('chữ thường');
        }

        // Uppercase check
        if (/[A-Z]/.test(password)) {
            score += 1;
        } else {
            feedback.push('chữ hoa');
        }

        // Number check
        if (/\d/.test(password)) {
            score += 1;
        } else {
            feedback.push('số');
        }

        // Special character check
        if (/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(password)) {
            score += 1;
        } else {
            feedback.push('ký tự đặc biệt');
        }

        const percentage = Math.min(100, (score / 5) * 100);

        return {
            score: score,
            percentage: percentage,
            feedback: feedback
        };
    }

    function setupPhoneFormatting() {
        const phoneInput = document.getElementById('phone');
        if (phoneInput) {
            phoneInput.addEventListener('input', (e) => {
                let value = e.target.value.replace(/\D/g, ''); // Remove non-digits

                // Format Vietnamese phone number
                if (value.startsWith('84') && value.length > 2) {
                    // International format: +84 XXX XXX XXX
                    value = value.replace(/(\d{2})(\d{3})(\d{3})(\d{3})/, '$1 $2 $3 $4');
                } else if (value.startsWith('0') && value.length > 1) {
                    // Local format: 0XXX XXX XXX
                    value = value.replace(/(\d{4})(\d{3})(\d{3})/, '$1 $2 $3');
                }

                e.target.value = value;
            });

            phoneInput.addEventListener('blur', () => {
                const value = phoneInput.value.replace(/\s/g, '');
                if (value && !validatePhone(value)) {
                    phoneInput.style.borderColor = '#dc3545';
                } else {
                    phoneInput.style.borderColor = '#dee2e6';
                }
            });
        }
    }

    // This showToast function requires Bootstrap's Toast component
    function showToast(message, type = 'success') {
        const toastPlaceholder = document.getElementById('toastPlaceholder');
        if (!toastPlaceholder) {
            console.warn("Toast placeholder not found. Add <div id='toastPlaceholder'></div> to your HTML.");
            alert(message); // Fallback to alert
            return;
        }

        // Remove existing toasts
        toastPlaceholder.innerHTML = '';

        const toastHtml = `
            <div class="toast-container position-fixed bottom-0 end-0 p-3">
                <div class="toast align-items-center text-white bg-${type} border-0" role="alert" aria-live="assertive" aria-atomic="true">
                    <div class="d-flex">
                        <div class="toast-body">
                            ${message}
                        </div>
                        <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
                    </div>
                </div>
            </div>
        `;

        toastPlaceholder.innerHTML = toastHtml;
        const toastElement = toastPlaceholder.querySelector('.toast');
        if (toastElement) {
            const toast = new bootstrap.Toast(toastElement);
            toast.show();
        }
    }


    function setupProfileValidation() {
        const profileForm = document.querySelector('form[action="/profile"]');
        if (profileForm) {
            const phone = document.querySelector('input[name="phone"]');
            if (phone) {
                phone.addEventListener('blur', () => {
                    if (phone.value && !validatePhone(phone.value)) {
                        alert('Số điện thoại không hợp lệ! (Định dạng Việt Nam)');
                    }
                });
            }

            profileForm.addEventListener('submit', (e) => {
                const fullName = document.querySelector('input[name="fullName"]');
                if (fullName.value.trim().length < 2) {
                    e.preventDefault();
                    alert('Tên đầy đủ phải có ít nhất 2 ký tự!');
                    return false;
                }
                if (phone.value && !validatePhone(phone.value)) {
                    e.preventDefault();
                    alert('Số điện thoại không hợp lệ!');
                    return false;
                }
                return true;
            });
        }
    }

    // Initialize auth validation when DOM loaded
    setupLoginValidation();
    setupRegisterValidation();
    setupProfileValidation();

    // ----------------------
    // Header scroll effect
    // ----------------------
    let lastScrollY = window.scrollY;
    const header = document.querySelector('.header');

    window.addEventListener('scroll', () => {
        const currentScrollY = window.scrollY;

        if (currentScrollY > lastScrollY && currentScrollY > 100) {
            // Scrolling down
            if (header) header.style.transform = 'translateY(-100%)';
        } else {
            // Scrolling up
            if (header) header.style.transform = 'translateY(0)';
        }

        lastScrollY = currentScrollY;
    });

    // ----------------------
    // Lazy loading for images
    // ----------------------
    const images = document.querySelectorAll('img[loading="lazy"]');
    if ('IntersectionObserver' in window) {
        let imageObserver = new IntersectionObserver((entries, observer) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    let img = entry.target;
                    let src = img.getAttribute('src');
                    if (src) {
                        img.src = src; // Thực sự tải ảnh
                        img.classList.add('fade-in'); // Thêm class để fade in
                    }
                    imageObserver.unobserve(img);
                }
            });
        });

        images.forEach(img => {
            imageObserver.observe(img);
        });
    } else {
        // Fallback cho trình duyệt không hỗ trợ IntersectionObserver
        images.forEach(img => {
            let src = img.getAttribute('src');
            if (src) {
                img.src = src;
            }
        });
    }

    // ----------------------
    // Error handling for failed image loads
    // ----------------------
    images.forEach(img => {
        img.addEventListener('error', () => {
            img.src = 'https://via.placeholder.com/300x200/f0f0f0/999999?text=Hình+ảnh+không+dùng+được';
            img.alt = 'Hình ảnh không dùng được';
        });
    });

    // ----------------------
    // Keyboard navigation support
    // ----------------------
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Tab') {
            document.body.classList.add('keyboard-navigation');
        }
    });

    document.addEventListener('mousedown', () => {
        document.body.classList.remove('keyboard-navigation');
    });

    console.log('Huy Hoang Auto 88 website loaded successfully!');
});
