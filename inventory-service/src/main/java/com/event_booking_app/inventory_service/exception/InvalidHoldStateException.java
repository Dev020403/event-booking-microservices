package com.event_booking_app.inventory_service.exception;

import com.event_booking_app.inventory_service.entity.HoldStatus;

import java.util.UUID;

public class InvalidHoldStateException extends RuntimeException {
    public InvalidHoldStateException(String message) {
        super(message);
    }

    public InvalidHoldStateException(UUID holdId, HoldStatus currentStatus, String attemptedAction) {
        super("Cannot " + attemptedAction + " hold " + holdId + " — current status is " + currentStatus);
    }
}
