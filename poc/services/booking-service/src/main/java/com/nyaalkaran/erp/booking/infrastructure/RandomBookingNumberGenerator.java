package com.nyaalkaran.erp.booking.infrastructure;

import com.nyaalkaran.erp.booking.domain.BookingNumberGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** BK-20261016-7Q4M2X: date + 6 random base-32 characters (the UNIQUE key catches the rare collision). */
@Component
public class RandomBookingNumberGenerator implements BookingNumberGenerator {

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public RandomBookingNumberGenerator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String next() {
        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return ("BK-" + LocalDate.now(clock).format(DAY) + "-" + suffix).toUpperCase(Locale.ROOT);
    }
}
