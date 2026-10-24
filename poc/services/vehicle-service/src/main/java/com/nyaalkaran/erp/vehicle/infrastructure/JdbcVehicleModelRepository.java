package com.nyaalkaran.erp.vehicle.infrastructure;

import com.nyaalkaran.erp.tenant.routing.TenantJdbc;
import com.nyaalkaran.erp.vehicle.domain.BodyType;
import com.nyaalkaran.erp.vehicle.domain.FuelType;
import com.nyaalkaran.erp.vehicle.domain.VehicleModel;
import com.nyaalkaran.erp.vehicle.domain.VehicleModelRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** vehicle_model in the current company's cmp_<n>_vehicle_db (schema: CP db/tenant/vehicle.sql). */
@Repository
public class JdbcVehicleModelRepository implements VehicleModelRepository {

    private static final RowMapper<VehicleModel> ROW = (rs, i) -> new VehicleModel(
            rs.getLong("id"), rs.getString("model_code"), rs.getString("name"),
            BodyType.valueOf(rs.getString("body_type")), FuelType.valueOf(rs.getString("fuel_type")),
            rs.getBigDecimal("base_price"), rs.getTimestamp("created_at").toLocalDateTime(), rs.getString("created_by"));

    private final TenantJdbc jdbc;

    public JdbcVehicleModelRepository(TenantJdbc jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public VehicleModel insert(VehicleModel m) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.current().sql("INSERT INTO vehicle_model (model_code, name, body_type, fuel_type, base_price, "
                        + "created_at, created_by) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(m.modelCode(), m.name(), m.bodyType().name(), m.fuelType().name(), m.basePrice(),
                        m.createdAt(), m.createdBy())
                .update(key);
        return new VehicleModel(key.getKey().longValue(), m.modelCode(), m.name(), m.bodyType(), m.fuelType(),
                m.basePrice(), m.createdAt(), m.createdBy());
    }

    @Override
    public Optional<VehicleModel> findByCode(String modelCode) {
        return jdbc.current().sql("SELECT * FROM vehicle_model WHERE model_code = ?").param(modelCode)
                .query(ROW).optional();
    }

    @Override
    public List<VehicleModel> findAll() {
        return jdbc.current().sql("SELECT * FROM vehicle_model ORDER BY model_code").query(ROW).list();
    }
}
