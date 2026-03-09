package com.group1.car_rental.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false, length = 16)
    private String role; // ADMIN, HOST, CUSTOMER

    @Enumerated(EnumType.STRING)
    @Column(name = "host_status")
    private HostStatus hostStatus = HostStatus.NONE;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "password_hash", nullable = false, length = 255)
    @JsonIgnore
    private String passwordHash;

    @Column(name = "twofa_enabled", nullable = false)
    private Boolean twofaEnabled = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;



    @Version
    private Integer version = 0;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private UserProfile profile;

    // Constructor for registration
    public User(String email, String passwordHash, String role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.hostStatus = HostStatus.NONE;
        this.isActive = true;
        this.twofaEnabled = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0;
    }
}
