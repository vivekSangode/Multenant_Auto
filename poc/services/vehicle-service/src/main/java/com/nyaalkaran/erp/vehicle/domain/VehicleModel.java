package com.nyaalkaran.erp.vehicle.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A car model of the company's catalog, e.g. "VX-SUV-2026". */
public record VehicleModel(Long id, String modelCode, String name, BodyType bodyType, FuelType fuelType,
                           BigDecimal basePrice, LocalDateTime createdAt, String createdBy) {
}
