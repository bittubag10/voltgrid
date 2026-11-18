package com.voltgrid.voltgrid.vehicle.repository;

import com.voltgrid.voltgrid.vehicle.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Page<Vehicle> findByDriverIdAndActiveTrue(Long driverId, Pageable pageable);

    Optional<Vehicle> findByIdAndDriverIdAndActiveTrue(Long id, Long driverId);

    long countByDriverIdAndActiveTrue(Long driverId);
}