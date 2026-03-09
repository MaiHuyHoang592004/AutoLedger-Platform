package com.group1.car_rental.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "complaints")
@Data
@NoArgsConstructor
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Người gửi khiếu nại
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complainant_user_id", nullable = false)
    private User complainant;

    // Tiêu đề
    @Column(nullable = false, length = 255)
    private String subject;

    // Nội dung chi tiết
    @Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String description;

    // Trạng thái: PENDING, IN_PROGRESS, RESOLVED, DISMISSED
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    // Ghi chú của Admin (khi giải quyết)
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String adminNotes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    private Integer version = 0;

    public Complaint(User complainant, String subject, String description) {
        this.complainant = complainant;
        this.subject = subject;
        this.description = description;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }
}