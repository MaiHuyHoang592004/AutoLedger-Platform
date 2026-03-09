package com.group1.car_rental.repository;

import com.group1.car_rental.entity.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    // Lấy tất cả khiếu nại và thông tin người gửi
    @Query("SELECT c FROM Complaint c JOIN FETCH c.complainant")
    List<Complaint> findAllWithComplainant();
}