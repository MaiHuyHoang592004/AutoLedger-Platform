package com.group1.car_rental.config;

import com.group1.car_rental.service.DbUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final DbUserDetailsService dbUserDetailsService;

    public SecurityConfig(DbUserDetailsService dbUserDetailsService) {
        this.dbUserDetailsService = dbUserDetailsService;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // === CSRF CONFIG ===
            .csrf(csrf -> csrf
                // Tắt CSRF cho API không cần (nếu bạn dùng Idempotency-Key + JWT sau này)
                .ignoringRequestMatchers("/host/api/bookings/**", "/bookings/api/reviews", "/api/reviews/**")
                // Hoặc giữ CSRF + dùng cookie (tốt cho web)
                // .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                // .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            )

            // === AUTHORIZATION RULES ===
            .authorizeHttpRequests(auth -> auth

                // PUBLIC: Không cần login
                .requestMatchers("/", "/register", "/login", "/search", "/css/**", "/js/**", "/images/**", "/uploads/**", "/favicon.ico").permitAll()

                // PUBLIC: Xem chi tiết xe (cho tất cả user đã login)
                .requestMatchers("/cars/{id:\\d+}").authenticated()

                // CUSTOMER: Đặt xe
                .requestMatchers("/rentals/**", "/bookings/create", "/bookings/payment/**").hasRole("CUSTOMER")

                // HOST: Quản lý xe + đơn đặt
                .requestMatchers("/cars/**").hasRole("HOST")
                .requestMatchers("/host/**", "/host/api/bookings/**").hasRole("HOST")

                // ADMIN
                .requestMatchers("/admin/**").hasRole("ADMIN")

                // REVIEWS API: Cần authenticated
                .requestMatchers(HttpMethod.POST, "/api/reviews/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/reviews/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/reviews/**").authenticated()

                // CÁC API KHÁC: Cần authenticated
                .requestMatchers(HttpMethod.POST, "/bookings/api/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/bookings/api/**").authenticated()

                // Bắt buộc login cho mọi request còn lại
                .anyRequest().authenticated()
            )

            // === FORM LOGIN ===
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler((request, response, authentication) -> {
                    // Redirect based on role
                    boolean isAdmin = authentication.getAuthorities().stream()
                        .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
                    if (isAdmin) {
                        response.sendRedirect("/admin/dashboard");
                    } else {
                        response.sendRedirect("/");
                    }
                })
                .failureUrl("/login?error=true")
                .permitAll()
            )

            // === LOGOUT ===
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .deleteCookies("JSESSIONID")
                .invalidateHttpSession(true)
                .permitAll()
            )

            // === USER DETAILS ===
            .userDetailsService(dbUserDetailsService);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
