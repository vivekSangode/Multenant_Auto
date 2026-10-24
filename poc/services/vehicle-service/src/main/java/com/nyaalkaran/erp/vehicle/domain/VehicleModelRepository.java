package com.nyaalkaran.erp.vehicle.domain;

import java.util.List;
import java.util.Optional;

/** Persistence port of the catalog. The implementation decides which company database it talks to. */
public interface VehicleModelRepository {

    VehicleModel insert(VehicleModel model);

    Optional<VehicleModel> findByCode(String modelCode);

    List<VehicleModel> findAll();
}
