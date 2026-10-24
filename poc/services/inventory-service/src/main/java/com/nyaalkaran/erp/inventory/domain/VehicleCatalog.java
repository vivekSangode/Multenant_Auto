package com.nyaalkaran.erp.inventory.domain;

/** What inventory needs to know from VEHICLE-SERVICE (interface segregation: nothing more). */
public interface VehicleCatalog {

    boolean modelExists(String modelCode);
}
