package com.nyaalkaran.erp.booking.application;

import com.nyaalkaran.erp.booking.domain.Booking;
import com.nyaalkaran.erp.booking.domain.Booking.BookingStatus;
import com.nyaalkaran.erp.booking.domain.BookingNumberGenerator;
import com.nyaalkaran.erp.booking.domain.BookingRepository;
import com.nyaalkaran.erp.booking.domain.VehiclePricing;
import com.nyaalkaran.erp.booking.domain.VehicleReservations;
import com.nyaalkaran.erp.tenant.web.ServiceException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Booking use cases. Depends only on ports (pricing, reservations, repository, numbering), so the same logic
 * is unit-tested with in-memory fakes and runs in production against the other services over HTTP.
 */
@Service
public class BookingService {

    public record NewBooking(String customerName, String customerPhone, String modelCode, String color) {
    }

    private final BookingRepository bookings;
    private final VehiclePricing pricing;
    private final VehicleReservations reservations;
    private final BookingNumberGenerator numbers;
    private final Clock clock;

    public BookingService(BookingRepository bookings, VehiclePricing pricing, VehicleReservations reservations,
                          BookingNumberGenerator numbers, Clock clock) {
        this.bookings = bookings;
        this.pricing = pricing;
        this.reservations = reservations;
        this.numbers = numbers;
        this.clock = clock;
    }

    /** Price -> reserve a car -> save. If saving fails, the reserved car is released again (no orphan holds). */
    public Booking book(NewBooking cmd, String actor) {
        String model = cmd.modelCode().toUpperCase(Locale.ROOT);
        String color = cmd.color().trim().toUpperCase(Locale.ROOT);
        BigDecimal price = pricing.priceOf(model).orElseThrow(() ->
                ServiceException.unprocessable("UNKNOWN_MODEL", "Model " + model + " is not in the vehicle catalog"));
        String bookingNo = numbers.next();
        String vin = reservations.reserve(bookingNo, model, color);
        try {
            return bookings.insert(new Booking(null, bookingNo, cmd.customerName().trim(), cmd.customerPhone(), model,
                    color, vin, price, BookingStatus.BOOKED, LocalDateTime.now(clock), actor));
        } catch (RuntimeException ex) {
            reservations.release(bookingNo);
            throw ex;
        }
    }

    public List<Booking> list() {
        return bookings.findAll();
    }

    public Booking get(String bookingNo) {
        return bookings.findByNo(bookingNo)
                .orElseThrow(() -> ServiceException.notFound("BOOKING_NOT_FOUND", "No booking " + bookingNo));
    }

    public Booking cancel(String bookingNo) {
        requireBooked(bookingNo);
        reservations.release(bookingNo);
        bookings.transition(bookingNo, BookingStatus.BOOKED, BookingStatus.CANCELLED, LocalDateTime.now(clock));
        return get(bookingNo);
    }

    public Booking deliver(String bookingNo) {
        requireBooked(bookingNo);
        reservations.sell(bookingNo);
        bookings.transition(bookingNo, BookingStatus.BOOKED, BookingStatus.DELIVERED, LocalDateTime.now(clock));
        return get(bookingNo);
    }

    private void requireBooked(String bookingNo) {
        Booking b = get(bookingNo);
        if (b.status() != BookingStatus.BOOKED) {
            throw ServiceException.conflict("INVALID_BOOKING_STATE", "Booking " + bookingNo + " is " + b.status());
        }
    }
}
