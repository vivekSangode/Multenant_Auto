package com.nyaalkaran.erp.inventory.infrastructure;

import com.nyaalkaran.erp.inventory.domain.StockUnit;
import com.nyaalkaran.erp.inventory.domain.StockUnit.StockStatus;
import com.nyaalkaran.erp.inventory.domain.StockUnitRepository;
import com.nyaalkaran.erp.tenant.routing.TenantJdbc;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** stock_unit in the current company's cmp_<n>_inventory_db (schema: CP db/tenant/inventory.sql). */
@Repository
public class JdbcStockUnitRepository implements StockUnitRepository {

    private static final RowMapper<StockUnit> ROW = (rs, i) -> new StockUnit(
            rs.getLong("id"), rs.getString("vin"), rs.getString("model_code"), rs.getString("color"),
            StockStatus.valueOf(rs.getString("status")), rs.getString("reserved_for"),
            rs.getTimestamp("created_at").toLocalDateTime(), rs.getString("created_by"));

    private final TenantJdbc jdbc;

    public JdbcStockUnitRepository(TenantJdbc jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public StockUnit insert(StockUnit u) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.current().sql("INSERT INTO stock_unit (vin, model_code, color, status, created_at, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")
                .params(u.vin(), u.modelCode(), u.color(), u.status().name(), u.createdAt(), u.createdBy())
                .update(key);
        return new StockUnit(key.getKey().longValue(), u.vin(), u.modelCode(), u.color(), u.status(), null,
                u.createdAt(), u.createdBy());
    }

    @Override
    public Optional<StockUnit> findByVin(String vin) {
        return jdbc.current().sql("SELECT * FROM stock_unit WHERE vin = ?").param(vin).query(ROW).optional();
    }

    @Override
    public List<StockUnit> find(Filter f) {
        StringBuilder sql = new StringBuilder("SELECT * FROM stock_unit WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (f.modelCode() != null) {
            sql.append(" AND model_code = ?");
            params.add(f.modelCode());
        }
        if (f.color() != null) {
            sql.append(" AND color = ?");
            params.add(f.color());
        }
        if (f.status() != null) {
            sql.append(" AND status = ?");
            params.add(f.status().name());
        }
        return jdbc.current().sql(sql.append(" ORDER BY id").toString()).params(params).query(ROW).list();
    }

    @Override
    public Optional<StockUnit> findByReservation(String reference) {
        return jdbc.current().sql("SELECT * FROM stock_unit WHERE reserved_for = ?").param(reference)
                .query(ROW).optional();
    }

    @Override
    public boolean reserveFirstAvailable(String modelCode, String color, String reference, LocalDateTime now) {
        // one atomic statement: concurrent bookings can never take the same VIN
        return jdbc.current().sql("UPDATE stock_unit SET status = 'RESERVED', reserved_for = ?, updated_at = ? "
                        + "WHERE model_code = ? AND color = ? AND status = 'AVAILABLE' ORDER BY id LIMIT 1")
                .params(reference, now, modelCode, color)
                .update() == 1;
    }

    @Override
    public int transition(String reference, StockStatus from, StockStatus to, boolean keepReference, LocalDateTime now) {
        return jdbc.current().sql("UPDATE stock_unit SET status = ?, updated_at = ?"
                        + (keepReference ? "" : ", reserved_for = NULL")
                        + " WHERE reserved_for = ? AND status = ?")
                .params(to.name(), now, reference, from.name())
                .update();
    }
}
