package com.nyaalkaran.erp.inventory.domain;

import com.nyaalkaran.erp.inventory.domain.StockUnit.StockStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence port of the stock. Reservation methods are atomic so two bookings never get the same car. */
public interface StockUnitRepository {

    record Filter(String modelCode, String color, StockStatus status) {
    }

    StockUnit insert(StockUnit unit);

    Optional<StockUnit> findByVin(String vin);

    List<StockUnit> find(Filter filter);

    Optional<StockUnit> findByReservation(String reference);

    /** Reserves the oldest AVAILABLE unit of (model, color) for the reference; false if none is left. */
    boolean reserveFirstAvailable(String modelCode, String color, String reference, LocalDateTime now);

    /** Moves the unit held by the reference from one status to another; returns the number of rows changed. */
    int transition(String reference, StockStatus from, StockStatus to, boolean keepReference, LocalDateTime now);
}
