package com.nyaalkaran.erp.booking.domain;

import com.nyaalkaran.erp.booking.domain.Booking.BookingStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository {

    Booking insert(Booking booking);

    Optional<Booking> findByNo(String bookingNo);

    List<Booking> findAll();

    /** Compare-and-set of the status; returns false when the booking was not in {@code from}. */
    boolean transition(String bookingNo, BookingStatus from, BookingStatus to, LocalDateTime now);
}
