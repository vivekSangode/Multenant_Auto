package com.nyaalkaran.erp.inventory.domain;

import java.time.LocalDateTime;

/** One physical car identified by its VIN. {@code reservedFor} is the booking reference holding it. */
public record StockUnit(Long id, String vin, String modelCode, String color, StockStatus status, String reservedFor,
                        LocalDateTime createdAt, String createdBy) {

    public enum StockStatus { AVAILABLE, RESERVED, SOLD }
}
