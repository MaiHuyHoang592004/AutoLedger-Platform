package com.group1.car_rental.service;

import com.group1.car_rental.entity.Complaint;
import com.group1.car_rental.entity.User;
import com.group1.car_rental.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintRepository complaintRepository;

    @Override
    @Transactional
    public Complaint submitComplaint(User complainant, String subject, String description) {
        if (subject == null || subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Tiêu đề không được trống");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Nội dung không được trống");
        }

        Complaint complaint = new Complaint(complainant, subject.trim(), description.trim());
        return complaintRepository.save(complaint);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Complaint> getAllComplaints() {
        // Sử dụng phương thức custom để fetch kèm user
        return complaintRepository.findAllWithComplainant();
    }

    @Override
    @Transactional(readOnly = true)
    public Complaint getComplaintById(Long id) {
        return complaintRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khiếu nại ID: " + id));
    }

    @Override
    @Transactional
    public Complaint resolveComplaint(Long complaintId, String adminNotes, String status) {
        if (status == null || (!"RESOLVED".equals(status) && !"IN_PROGRESS".equals(status) && !"DISMISSED".equals(status))) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ");
        }
        
        Complaint complaint = getComplaintById(complaintId);
        complaint.setAdminNotes(adminNotes);
        complaint.setStatus(status);
        
        if ("RESOLVED".equals(status) || "DISMISSED".equals(status)) {
            complaint.setResolvedAt(Instant.now());
        }

        return complaintRepository.save(complaint);
    }
}