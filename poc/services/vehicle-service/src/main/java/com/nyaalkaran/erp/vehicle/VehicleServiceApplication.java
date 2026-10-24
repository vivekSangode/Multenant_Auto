package com.nyaalkaran.erp.vehicle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/** VEHICLE-SERVICE: the car models a dealer sells. One deployment serves every company (tenant-starter). */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class VehicleServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VehicleServiceApplication.class, args);
    }
}
