package com.nyaalkaran.erp.booking.api;

import com.nyaalkaran.erp.booking.application.BookingService;
import com.nyaalkaran.erp.booking.application.BookingService.NewBooking;
import com.nyaalkaran.erp.booking.domain.Booking;
import com.nyaalkaran.erp.tenant.context.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/booking/bookings")
public class BookingController {

    public record NewBookingRequest(@NotBlank @Size(max = 150) String customerName,
                                    @Size(max = 30) String customerPhone,
                                    @NotBlank @Size(max = 40) String modelCode,
                                    @NotBlank @Size(max = 40) String color) {
    }

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Booking book(@Valid @RequestBody NewBookingRequest req) {
        return bookings.book(new NewBooking(req.customerName(), req.customerPhone(), req.modelCode(), req.color()),
                TenantContext.require().username());
    }

    @GetMapping
    public List<Booking> list() {
        return bookings.list();
    }

    @GetMapping("/{bookingNo}")
    public Booking get(@PathVariable String bookingNo) {
        return bookings.get(bookingNo);
    }

    @PostMapping("/{bookingNo}/cancel")
    public Booking cancel(@PathVariable String bookingNo) {
        return bookings.cancel(bookingNo);
    }

    @PostMapping("/{bookingNo}/deliver")
    public Booking deliver(@PathVariable String bookingNo) {
        return bookings.deliver(bookingNo);
    }
}
