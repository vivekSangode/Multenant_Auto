package com.nyaalkaran.erp.vehicle.application;

import com.nyaalkaran.erp.vehicle.application.VehicleCatalogService.AddModel;
import com.nyaalkaran.erp.vehicle.domain.BodyType;
import com.nyaalkaran.erp.vehicle.domain.FuelType;
import com.nyaalkaran.erp.vehicle.domain.VehicleModel;
import com.nyaalkaran.erp.vehicle.domain.VehicleModelRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VehicleCatalogServiceTest {

    static final class Models implements VehicleModelRepository {
        final List<VehicleModel> rows = new ArrayList<>();

        @Override
        public VehicleModel insert(VehicleModel m) {
            VehicleModel saved = new VehicleModel((long) rows.size() + 1, m.modelCode(), m.name(), m.bodyType(),
                    m.fuelType(), m.basePrice(), m.createdAt(), m.createdBy());
            rows.add(saved);
            return saved;
        }

        @Override
        public Optional<VehicleModel> findByCode(String code) {
            return rows.stream().filter(m -> m.modelCode().equals(code)).findFirst();
        }

        @Override
        public List<VehicleModel> findAll() {
            return rows;
        }
    }

    private final VehicleCatalogService service = new VehicleCatalogService(new Models(), Clock.systemUTC());

    @Test
    void codesAreNormalizedAndUnique() {
        VehicleModel m = service.add(new AddModel(" vx-suv ", "VX SUV", BodyType.SUV, FuelType.DIESEL,
                new BigDecimal("1450000")), "owner");
        assertThat(m.modelCode()).isEqualTo("VX-SUV");
        assertThat(service.get("vx-suv").name()).isEqualTo("VX SUV");
        assertThatThrownBy(() -> service.add(new AddModel("VX-SUV", "Again", BodyType.SUV, FuelType.PETROL,
                BigDecimal.TEN), "owner")).hasFieldOrPropertyWithValue("code", "MODEL_EXISTS");
        assertThatThrownBy(() -> service.get("NOPE")).hasFieldOrPropertyWithValue("code", "MODEL_NOT_FOUND");
    }
}
