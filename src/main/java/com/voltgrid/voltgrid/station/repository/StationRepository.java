package com.voltgrid.voltgrid.station.repository;

import com.voltgrid.voltgrid.station.entity.Station;
import com.voltgrid.voltgrid.station.entity.StationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StationRepository extends JpaRepository<Station, Long> {

    // Owner side (ownership check is part of the query)
    Page<Station> findByOwnerId(Long ownerId, Pageable pageable);

    Optional<Station> findByIdAndOwnerId(Long id, Long ownerId);

    long countByOwnerId(Long ownerId);

    // Public side (only ACTIVE stations)
    Page<Station> findByStatus(StationStatus status, Pageable pageable);

    Page<Station> findByStatusAndCityIgnoreCase(StationStatus status, String city, Pageable pageable);

    Optional<Station> findByIdAndStatus(Long id, StationStatus status);
    @Query("""
            select s from Station s
            where s.status = :status
              and s.latitude between :minLat and :maxLat
              and s.longitude between :minLng and :maxLng
            """)
    List<Station> findInBox(@Param("status") StationStatus status,
                            @Param("minLat") BigDecimal minLat,
                            @Param("maxLat") BigDecimal maxLat,
                            @Param("minLng") BigDecimal minLng,
                            @Param("maxLng") BigDecimal maxLng);
}