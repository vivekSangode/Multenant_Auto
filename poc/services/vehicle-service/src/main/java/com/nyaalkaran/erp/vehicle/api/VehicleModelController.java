package com.nyaalkaran.erp.vehicle.api;

import com.nyaalkaran.erp.tenant.context.TenantContext;
import com.nyaalkaran.erp.vehicle.application.VehicleCatalogService;
import com.nyaalkaran.erp.vehicle.application.VehicleCatalogService.AddModel;
import com.nyaalkaran.erp.vehicle.domain.BodyType;
import com.nyaalkaran.erp.vehicle.domain.FuelType;
import com.nyaalkaran.erp.vehicle.domain.VehicleModel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** HTTP adapter only: validation + mapping. The same API serves every company; the token decides which one. */
@RestController
@RequestMapping("/api/v1/vehicle/models")
public class VehicleModelController {

    public record AddModelRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9-]{2,40}") String modelCode,
                                  @NotBlank @Size(max = 150) String name,
                                  @NotNull BodyType bodyType,
                                  @NotNull FuelType fuelType,
                                  @NotNull @DecimalMin("1") BigDecimal basePrice) {
    }

    private final VehicleCatalogService catalog;

    public VehicleModelController(VehicleCatalogService catalog) {
        this.catalog = catalog;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleModel add(@Valid @RequestBody AddModelRequest req) {
        return catalog.add(new AddModel(req.modelCode(), req.name(), req.bodyType(), req.fuelType(), req.basePrice()),
                TenantContext.require().username());
    }

    @GetMapping
    public List<VehicleModel> list() {
        return catalog.list();
    }

    @GetMapping("/{modelCode}")
    public VehicleModel get(@PathVariable String modelCode) {
        return catalog.get(modelCode);
    }
}
