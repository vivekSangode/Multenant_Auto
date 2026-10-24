package com.nyaalkaran.erp.vehicle.application;

import com.nyaalkaran.erp.tenant.web.ServiceException;
import com.nyaalkaran.erp.vehicle.domain.BodyType;
import com.nyaalkaran.erp.vehicle.domain.FuelType;
import com.nyaalkaran.erp.vehicle.domain.VehicleModel;
import com.nyaalkaran.erp.vehicle.domain.VehicleModelRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** Catalog use cases. Knows nothing about tenants: the repository is already bound to the caller's company. */
@Service
public class VehicleCatalogService {

    public record AddModel(String modelCode, String name, BodyType bodyType, FuelType fuelType, BigDecimal basePrice) {
    }

    private final VehicleModelRepository models;
    private final Clock clock;

    public VehicleCatalogService(VehicleModelRepository models, Clock clock) {
        this.models = models;
        this.clock = clock;
    }

    public VehicleModel add(AddModel cmd, String actor) {
        String code = cmd.modelCode().trim().toUpperCase(Locale.ROOT);
        if (models.findByCode(code).isPresent()) {
            throw ServiceException.conflict("MODEL_EXISTS", "Model " + code + " already exists");
        }
        return models.insert(new VehicleModel(null, code, cmd.name().trim(), cmd.bodyType(), cmd.fuelType(),
                cmd.basePrice(), LocalDateTime.now(clock), actor));
    }

    public List<VehicleModel> list() {
        return models.findAll();
    }

    public VehicleModel get(String modelCode) {
        return models.findByCode(modelCode.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ServiceException.notFound("MODEL_NOT_FOUND", "No model " + modelCode));
    }
}
