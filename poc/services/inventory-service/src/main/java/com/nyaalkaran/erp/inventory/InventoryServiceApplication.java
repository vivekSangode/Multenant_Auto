package com.nyaalkaran.erp.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/** INVENTORY-SERVICE: the physical cars (VINs) a dealer has in stock, and their reservations. */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
