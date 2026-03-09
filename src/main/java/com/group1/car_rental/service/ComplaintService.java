package com.group1.car_rental.service;

import com.group1.car_rental.entity.Complaint;
import com.group1.car_rental.entity.User;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public interface ComplaintService {

    /**
     * Người dùng gửi một khiếu nại mới
     */
    Complaint submitComplaint(User complainant, String subject, String description);

    /**
     * Admin xem tất cả khiếu nại
     */
    List<Complaint> getAllComplaints();

    /**
     * Admin xem chi tiết một khiếu nại
     */
    Complaint getComplaintById(Long id);

    /**
     * Admin giải quyết một khiếu nại
     */
    Complaint resolveComplaint(Long complaintId, String adminNotes, String status);
}