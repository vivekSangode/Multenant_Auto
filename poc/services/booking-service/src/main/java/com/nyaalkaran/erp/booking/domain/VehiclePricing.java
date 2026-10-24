package com.nyaalkaran.erp.booking.domain;

import java.math.BigDecimal;
import java.util.Optional;

/** The one thing booking needs from VEHICLE-SERVICE. */
public interface VehiclePricing {

    Optional<BigDecimal> priceOf(String modelCode);
}
