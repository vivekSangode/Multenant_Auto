package com.nyaalkaran.erp.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/** BOOKING-SERVICE: customer bookings; prices from VEHICLE-SERVICE, cars reserved in INVENTORY-SERVICE. */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
