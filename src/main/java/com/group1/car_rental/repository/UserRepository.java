package com.group1.car_rental.repository;

import com.group1.car_rental.entity.User;
import com.group1.car_rental.entity.HostStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.profile WHERE u.email = :email")
    Optional<User> findByEmailWithProfile(String email);

    @Query("SELECT u FROM User u WHERE u.email = :username")
    Optional<User> findByUsername(@Param("username") String username);

    // Find users by host status
    List<User> findAllByHostStatus(HostStatus hostStatus);
}
