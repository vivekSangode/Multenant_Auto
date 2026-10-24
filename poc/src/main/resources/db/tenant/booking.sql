--liquibase formatted sql

-- Tenant changelog of BOOKING-SERVICE. Runs inside each company's own cmp_<n>_booking_db (master §16.5).

--changeset booking:001-booking
CREATE TABLE vehicle_booking (
    id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_no     VARCHAR(30)   NOT NULL,
    customer_name  VARCHAR(150)  NOT NULL,
    customer_phone VARCHAR(30)   NULL,
    model_code     VARCHAR(40)   NOT NULL,
    color          VARCHAR(40)   NOT NULL,
    vin            VARCHAR(17)   NOT NULL,
    price          DECIMAL(15,2) NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    created_by     VARCHAR(100)  NULL,
    updated_at     DATETIME(6)   NULL,
    CONSTRAINT uk_vehicle_booking_no UNIQUE (booking_no)
);
