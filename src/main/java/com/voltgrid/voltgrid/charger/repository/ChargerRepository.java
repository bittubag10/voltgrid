package com.voltgrid.voltgrid.charger.repository;

import com.voltgrid.voltgrid.charger.entity.Charger;
import com.voltgrid.voltgrid.charger.entity.ChargerStatus;
import com.voltgrid.voltgrid.station.entity.StationStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChargerRepository extends JpaRepository<Charger, Long> {

    @Query("""
            select c from Charger c
            where c.id = :id
              and c.station.id = :stationId
              and c.station.owner.id = :ownerId
            """)
    Optional<Charger> findOwned(@Param("id") Long id,
                                @Param("stationId") Long stationId,
                                @Param("ownerId") Long ownerId);

    List<Charger> findByStationIdOrderByIdAsc(Long stationId);

    List<Charger> findByStationIdAndStatusNotOrderByIdAsc(Long stationId, ChargerStatus status);

    long countByStationId(Long stationId);

    boolean existsByStationIdAndNameIgnoreCase(Long stationId, String name);

    boolean existsByStationIdAndNameIgnoreCaseAndIdNot(Long stationId, String name, Long id);
    List<Charger> findByStationIdIn(Collection<Long> stationIds);
    List<Charger> findByStationIdInAndStatusNot(Collection<Long> stationIds, ChargerStatus status);

    @Query("""
            select c from Charger c join fetch c.station s
            where c.id = :id
              and s.status = :stationStatus
              and c.status <> :outOfService
            """)
    Optional<Charger> findBookable(@Param("id") Long id,
                                   @Param("stationStatus") StationStatus stationStatus,
                                   @Param("outOfService") ChargerStatus outOfService);

    long countByStatus(ChargerStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000")})
    @Query("select c from Charger c where c.id = :id")
    Optional<Charger> findByIdForUpdate(@Param("id") Long id);
}