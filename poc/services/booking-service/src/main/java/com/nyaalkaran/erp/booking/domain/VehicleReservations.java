package com.nyaalkaran.erp.booking.domain;

/** What booking needs from INVENTORY-SERVICE: hold a car for a booking, give it back, or hand it over. */
public interface VehicleReservations {

    /** @return the VIN reserved for the reference; throws OUT_OF_STOCK when no car is available */
    String reserve(String reference, String modelCode, String color);

    void release(String reference);

    void sell(String reference);
}
