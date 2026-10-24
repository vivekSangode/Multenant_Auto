package com.nyaalkaran.erp.booking.application;

import com.nyaalkaran.erp.booking.application.BookingService.NewBooking;
import com.nyaalkaran.erp.booking.domain.Booking;
import com.nyaalkaran.erp.booking.domain.Booking.BookingStatus;
import com.nyaalkaran.erp.booking.domain.BookingRepository;
import com.nyaalkaran.erp.booking.domain.VehiclePricing;
import com.nyaalkaran.erp.booking.domain.VehicleReservations;
import com.nyaalkaran.erp.tenant.web.ServiceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** BookingService against in-memory fakes of its ports: no HTTP, no database, no Spring. */
class BookingServiceTest {

    static final class Bookings implements BookingRepository {
        final Map<String, Booking> rows = new HashMap<>();
        boolean failNextInsert;

        @Override
        public Booking insert(Booking b) {
            if (failNextInsert) {
                throw new IllegalStateException("db down");
            }
            Booking saved = new Booking((long) rows.size() + 1, b.bookingNo(), b.customerName(), b.customerPhone(),
                    b.modelCode(), b.color(), b.vin(), b.price(), b.status(), b.createdAt(), b.createdBy());
            rows.put(b.bookingNo(), saved);
            return saved;
        }

        @Override
        public Optional<Booking> findByNo(String bookingNo) {
            return Optional.ofNullable(rows.get(bookingNo));
        }

        @Override
        public List<Booking> findAll() {
            return new ArrayList<>(rows.values());
        }

        @Override
        public boolean transition(String no, BookingStatus from, BookingStatus to, LocalDateTime now) {
            Booking b = rows.get(no);
            if (b == null || b.status() != from) {
                return false;
            }
            rows.put(no, new Booking(b.id(), no, b.customerName(), b.customerPhone(), b.modelCode(), b.color(),
                    b.vin(), b.price(), to, b.createdAt(), b.createdBy()));
            return true;
        }
    }

    static final class Stock implements VehicleReservations {
        final Deque<String> available = new ArrayDeque<>(List.of("MA1VIN00000000001", "MA1VIN00000000002"));
        final Map<String, String> reserved = new HashMap<>();
        final List<String> sold = new ArrayList<>();

        @Override
        public String reserve(String reference, String modelCode, String color) {
            if (available.isEmpty()) {
                throw ServiceException.conflict("OUT_OF_STOCK", "none left");
            }
            String vin = available.pop();
            reserved.put(reference, vin);
            return vin;
        }

        @Override
        public void release(String reference) {
            available.push(reserved.remove(reference));
        }

        @Override
        public void sell(String reference) {
            sold.add(reserved.remove(reference));
        }
    }

    private final Bookings bookings = new Bookings();
    private final Stock stock = new Stock();
    private final VehiclePricing pricing = model -> "VX-SUV".equals(model)
            ? Optional.of(new BigDecimal("1450000")) : Optional.empty();
    private final AtomicInteger seq = new AtomicInteger();
    private final BookingService service = new BookingService(bookings, pricing, stock,
            () -> "BK-" + seq.incrementAndGet(), Clock.fixed(Instant.parse("2026-10-16T10:00:00Z"), ZoneOffset.UTC));

    private Booking bookOne() {
        return service.book(new NewBooking("Ravi Kumar", "9800000001", "vx-suv", " red "), "owner@velocity.test");
    }

    @Test
    void bookingReservesACarAtTheCatalogPrice() {
        Booking b = bookOne();
        assertThat(b.bookingNo()).isEqualTo("BK-1");
        assertThat(b.vin()).isEqualTo("MA1VIN00000000001");
        assertThat(b.price()).isEqualByComparingTo("1450000");
        assertThat(b.modelCode()).isEqualTo("VX-SUV");
        assertThat(b.color()).isEqualTo("RED");
        assertThat(b.status()).isEqualTo(BookingStatus.BOOKED);
        assertThat(stock.reserved).containsEntry("BK-1", "MA1VIN00000000001");
    }

    @Test
    void unknownModelIsRefusedBeforeAnythingIsReserved() {
        assertThatThrownBy(() -> service.book(new NewBooking("A", null, "NOPE", "RED"), "u"))
                .isInstanceOf(ServiceException.class).hasFieldOrPropertyWithValue("code", "UNKNOWN_MODEL");
        assertThat(stock.reserved).isEmpty();
    }

    @Test
    void outOfStockCreatesNoBooking() {
        bookOne();
        bookOne();
        assertThatThrownBy(this::bookOne).hasFieldOrPropertyWithValue("code", "OUT_OF_STOCK");
        assertThat(bookings.rows).hasSize(2);
    }

    @Test
    void failedSaveReleasesTheReservedCar() {
        bookings.failNextInsert = true;
        assertThatThrownBy(this::bookOne).isInstanceOf(IllegalStateException.class);
        assertThat(stock.reserved).isEmpty();
        assertThat(stock.available).hasSize(2);
    }

    @Test
    void cancelReleasesAndDeliverSells() {
        Booking first = bookOne();
        Booking second = bookOne();
        assertThat(service.cancel(first.bookingNo()).status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(stock.available).contains(first.vin());
        assertThat(service.deliver(second.bookingNo()).status()).isEqualTo(BookingStatus.DELIVERED);
        assertThat(stock.sold).containsExactly(second.vin());
        assertThatThrownBy(() -> service.cancel(second.bookingNo()))
                .hasFieldOrPropertyWithValue("code", "INVALID_BOOKING_STATE");
    }
}
