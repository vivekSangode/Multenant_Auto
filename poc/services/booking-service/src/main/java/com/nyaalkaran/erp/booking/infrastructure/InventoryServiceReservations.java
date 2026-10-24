package com.nyaalkaran.erp.booking.infrastructure;

import com.nyaalkaran.erp.booking.domain.VehicleReservations;
import com.nyaalkaran.erp.tenant.outbound.TenantRestClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** VehicleReservations over HTTP: INVENTORY-SERVICE /reservations, called with the caller's token. */
@Component
public class InventoryServiceReservations implements VehicleReservations {

    record UnitView(String vin) {
    }

    private final RestClient inventoryService;

    public InventoryServiceReservations(TenantRestClients clients, @Value("${services.inventory.url}") String url) {
        this.inventoryService = clients.forService(url);
    }

    @Override
    public String reserve(String reference, String modelCode, String color) {
        UnitView unit = inventoryService.post().uri("/api/v1/inventory/reservations")
                .body(Map.of("reference", reference, "modelCode", modelCode, "color", color))
                .retrieve().body(UnitView.class);
        if (unit == null || unit.vin() == null) {
            throw new IllegalStateException("INVENTORY-SERVICE returned no unit for " + reference);
        }
        return unit.vin();
    }

    @Override
    public void release(String reference) {
        inventoryService.delete().uri("/api/v1/inventory/reservations/{ref}", reference).retrieve().toBodilessEntity();
    }

    @Override
    public void sell(String reference) {
        inventoryService.post().uri("/api/v1/inventory/reservations/{ref}/sell", reference).retrieve().toBodilessEntity();
    }
}
