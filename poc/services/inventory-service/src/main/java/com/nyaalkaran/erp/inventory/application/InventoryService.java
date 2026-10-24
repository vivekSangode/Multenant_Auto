package com.nyaalkaran.erp.inventory.application;

import com.nyaalkaran.erp.inventory.domain.StockUnit;
import com.nyaalkaran.erp.inventory.domain.StockUnit.StockStatus;
import com.nyaalkaran.erp.inventory.domain.StockUnitRepository;
import com.nyaalkaran.erp.inventory.domain.StockUnitRepository.Filter;
import com.nyaalkaran.erp.inventory.domain.VehicleCatalog;
import com.nyaalkaran.erp.tenant.web.ServiceException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** Stock use cases: receive cars, list them, reserve / release / sell them for bookings. */
@Service
public class InventoryService {

    public record ReceiveUnit(String vin, String modelCode, String color) {
    }

    private final StockUnitRepository units;
    private final VehicleCatalog catalog;
    private final Clock clock;

    public InventoryService(StockUnitRepository units, VehicleCatalog catalog, Clock clock) {
        this.units = units;
        this.catalog = catalog;
        this.clock = clock;
    }

    public StockUnit receive(ReceiveUnit cmd, String actor) {
        String vin = cmd.vin().toUpperCase(Locale.ROOT);
        String model = cmd.modelCode().toUpperCase(Locale.ROOT);
        if (!catalog.modelExists(model)) {
            throw ServiceException.unprocessable("UNKNOWN_MODEL", "Model " + model + " is not in the vehicle catalog");
        }
        if (units.findByVin(vin).isPresent()) {
            throw ServiceException.conflict("VIN_EXISTS", "VIN " + vin + " is already in stock");
        }
        return units.insert(new StockUnit(null, vin, model, normalizeColor(cmd.color()), StockStatus.AVAILABLE, null,
                LocalDateTime.now(clock), actor));
    }

    public List<StockUnit> list(String modelCode, String color, StockStatus status) {
        return units.find(new Filter(modelCode == null ? null : modelCode.toUpperCase(Locale.ROOT),
                color == null ? null : normalizeColor(color), status));
    }

    /** Idempotent per reference: asking again returns the unit already reserved for it. */
    public StockUnit reserve(String reference, String modelCode, String color) {
        return units.findByReservation(reference).orElseGet(() -> {
            String model = modelCode.toUpperCase(Locale.ROOT);
            if (!units.reserveFirstAvailable(model, normalizeColor(color), reference, LocalDateTime.now(clock))) {
                throw ServiceException.conflict("OUT_OF_STOCK", "No AVAILABLE " + model + " in " + color);
            }
            return units.findByReservation(reference).orElseThrow();
        });
    }

    public void release(String reference) {
        if (units.transition(reference, StockStatus.RESERVED, StockStatus.AVAILABLE, false, LocalDateTime.now(clock)) == 0) {
            throw ServiceException.notFound("RESERVATION_NOT_FOUND", "No reserved unit for " + reference);
        }
    }

    public StockUnit sell(String reference) {
        if (units.transition(reference, StockStatus.RESERVED, StockStatus.SOLD, true, LocalDateTime.now(clock)) == 0) {
            throw ServiceException.notFound("RESERVATION_NOT_FOUND", "No reserved unit for " + reference);
        }
        return units.findByReservation(reference).orElseThrow();
    }

    private static String normalizeColor(String color) {
        return color.trim().toUpperCase(Locale.ROOT);
    }
}
