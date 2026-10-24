package com.nyaalkaran.erp.booking.infrastructure;

import com.nyaalkaran.erp.booking.domain.Booking;
import com.nyaalkaran.erp.booking.domain.Booking.BookingStatus;
import com.nyaalkaran.erp.booking.domain.BookingRepository;
import com.nyaalkaran.erp.tenant.routing.TenantJdbc;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** vehicle_booking in the current company's cmp_<n>_booking_db (schema: CP db/tenant/booking.sql). */
@Repository
public class JdbcBookingRepository implements BookingRepository {

    private static final RowMapper<Booking> ROW = (rs, i) -> new Booking(
            rs.getLong("id"), rs.getString("booking_no"), rs.getString("customer_name"),
            rs.getString("customer_phone"), rs.getString("model_code"), rs.getString("color"), rs.getString("vin"),
            rs.getBigDecimal("price"), BookingStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toLocalDateTime(), rs.getString("created_by"));

    private final TenantJdbc jdbc;

    public JdbcBookingRepository(TenantJdbc jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Booking insert(Booking b) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.current().sql("INSERT INTO vehicle_booking (booking_no, customer_name, customer_phone, model_code, color, "
                        + "vin, price, status, created_at, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(b.bookingNo(), b.customerName(), b.customerPhone(), b.modelCode(), b.color(), b.vin(),
                        b.price(), b.status().name(), b.createdAt(), b.createdBy())
                .update(key);
        return new Booking(key.getKey().longValue(), b.bookingNo(), b.customerName(), b.customerPhone(),
                b.modelCode(), b.color(), b.vin(), b.price(), b.status(), b.createdAt(), b.createdBy());
    }

    @Override
    public Optional<Booking> findByNo(String bookingNo) {
        return jdbc.current().sql("SELECT * FROM vehicle_booking WHERE booking_no = ?").param(bookingNo)
                .query(ROW).optional();
    }

    @Override
    public List<Booking> findAll() {
        return jdbc.current().sql("SELECT * FROM vehicle_booking ORDER BY id").query(ROW).list();
    }

    @Override
    public boolean transition(String bookingNo, BookingStatus from, BookingStatus to, LocalDateTime now) {
        return jdbc.current().sql("UPDATE vehicle_booking SET status = ?, updated_at = ? WHERE booking_no = ? AND status = ?")
                .params(to.name(), now, bookingNo, from.name())
                .update() == 1;
    }
}
