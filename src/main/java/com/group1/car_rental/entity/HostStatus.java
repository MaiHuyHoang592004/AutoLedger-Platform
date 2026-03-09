package com.group1.car_rental.entity;

public enum HostStatus {
    NONE,       // user bình thường, chưa xin
    PENDING,    // đã xin làm host, đang chờ duyệt
    APPROVED,   // đã được duyệt là host
    REJECTED    // bị từ chối (có thể xin lại sau)
}
