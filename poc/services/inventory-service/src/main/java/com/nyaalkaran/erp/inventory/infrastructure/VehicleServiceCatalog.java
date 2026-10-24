package com.nyaalkaran.erp.inventory.infrastructure;

import com.nyaalkaran.erp.inventory.domain.VehicleCatalog;
import com.nyaalkaran.erp.tenant.outbound.TenantRestClients;
import com.nyaalkaran.erp.tenant.web.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** VehicleCatalog over HTTP: GET VEHICLE-SERVICE /models/{code} with the caller's token (same company). */
@Component
public class VehicleServiceCatalog implements VehicleCatalog {

    private final RestClient vehicleService;

    public VehicleServiceCatalog(TenantRestClients clients, @Value("${services.vehicle.url}") String url) {
        this.vehicleService = clients.forService(url);
    }

    @Override
    public boolean modelExists(String modelCode) {
        try {
            vehicleService.get().uri("/api/v1/vehicle/models/{code}", modelCode).retrieve().toBodilessEntity();
            return true;
        } catch (ServiceException ex) {
            if (ex.status() == HttpStatus.NOT_FOUND) {
                return false;
            }
            throw ex;
        }
    }
}
