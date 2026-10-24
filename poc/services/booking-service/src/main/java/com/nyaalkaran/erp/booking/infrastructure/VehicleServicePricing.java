package com.nyaalkaran.erp.booking.infrastructure;

import com.nyaalkaran.erp.booking.domain.VehiclePricing;
import com.nyaalkaran.erp.tenant.outbound.TenantRestClients;
import com.nyaalkaran.erp.tenant.web.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

/** VehiclePricing over HTTP: the model's basePrice from VEHICLE-SERVICE, called with the caller's token. */
@Component
public class VehicleServicePricing implements VehiclePricing {

    record ModelView(String modelCode, BigDecimal basePrice) {
    }

    private final RestClient vehicleService;

    public VehicleServicePricing(TenantRestClients clients, @Value("${services.vehicle.url}") String url) {
        this.vehicleService = clients.forService(url);
    }

    @Override
    public Optional<BigDecimal> priceOf(String modelCode) {
        try {
            ModelView model = vehicleService.get().uri("/api/v1/vehicle/models/{code}", modelCode)
                    .retrieve().body(ModelView.class);
            return Optional.ofNullable(model).map(ModelView::basePrice);
        } catch (ServiceException ex) {
            if (ex.status() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw ex;
        }
    }
}
