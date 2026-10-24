package com.nyaalkaran.erp.booking.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A customer's booking of one car (VIN) at the catalog price of the day. */
public record Booking(Long id, String bookingNo, String customerName, String customerPhone, String modelCode,
                      String color, String vin, BigDecimal price, BookingStatus status, LocalDateTime createdAt,
                      String createdBy) {

    public enum BookingStatus { BOOKED, CANCELLED, DELIVERED }
}
