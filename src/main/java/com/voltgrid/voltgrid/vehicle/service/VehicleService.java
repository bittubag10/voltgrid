package com.voltgrid.voltgrid.vehicle.service;


import com.voltgrid.voltgrid.dto.PageResponse;
import com.voltgrid.voltgrid.exception.BadRequestException;
import com.voltgrid.voltgrid.exception.ResourceNotFoundException;
import com.voltgrid.voltgrid.repository.UserRepository;
import com.voltgrid.voltgrid.vehicle.dto.VehicleRequest;
import com.voltgrid.voltgrid.vehicle.dto.VehicleResponse;
import com.voltgrid.voltgrid.vehicle.entity.Vehicle;
import com.voltgrid.voltgrid.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private static final int MAX_VEHICLES_PER_DRIVER = 5;

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    @Transactional
    public VehicleResponse create(Long driverId, VehicleRequest request) {
        if (vehicleRepository.countByDriverIdAndActiveTrue(driverId) >= MAX_VEHICLES_PER_DRIVER) {
            throw new BadRequestException("You can register at most " + MAX_VEHICLES_PER_DRIVER + " vehicles");
        }

        Vehicle vehicle = Vehicle.builder()
                .driver(userRepository.getReferenceById(driverId))
                .model(request.model().trim())
                .usableBatteryKwh(request.usableBatteryKwh())
                .baseConsumptionKwhPerKm(request.baseConsumptionKwhPerKm())
                .maxChargeKw(request.maxChargeKw())
                .connectorType(request.connectorType())
                .build();

        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> list(Long driverId, Pageable pageable) {
        return PageResponse.from(
                vehicleRepository.findByDriverIdAndActiveTrue(driverId, pageable)
                        .map(VehicleResponse::from));
    }

    @Transactional(readOnly = true)
    public VehicleResponse get(Long driverId, Long vehicleId) {
        return VehicleResponse.from(findOwned(driverId, vehicleId));
    }

    @Transactional
    public VehicleResponse update(Long driverId, Long vehicleId, VehicleRequest request) {
        Vehicle vehicle = findOwned(driverId, vehicleId);
        vehicle.setModel(request.model().trim());
        vehicle.setUsableBatteryKwh(request.usableBatteryKwh());
        vehicle.setBaseConsumptionKwhPerKm(request.baseConsumptionKwhPerKm());
        vehicle.setMaxChargeKw(request.maxChargeKw());
        vehicle.setConnectorType(request.connectorType());
        return VehicleResponse.from(vehicle);
    }

    @Transactional
    public void delete(Long driverId, Long vehicleId) {
        findOwned(driverId, vehicleId).setActive(false);
    }

    /**
     * Ownership check: a vehicle that belongs to someone else is reported as "not found",
     * so other drivers cannot even learn that the id exists.
     */
    private Vehicle findOwned(Long driverId, Long vehicleId) {
        return vehicleRepository.findByIdAndDriverIdAndActiveTrue(vehicleId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
    }
}