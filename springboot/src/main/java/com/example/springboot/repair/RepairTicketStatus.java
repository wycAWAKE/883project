package com.example.springboot.repair;

public enum RepairTicketStatus {
    SUBMITTED, ASSIGNED, IN_PROGRESS, RESOLVED, CLOSED;
    public static String validateFilter(String status) {
        if (status == null || status.trim().isEmpty()) return null;
        try {
            return valueOf(status.trim()).name();
        } catch (IllegalArgumentException ex) {
            throw M4ApiException.badRequest("Invalid ticket status.");
        }
    }
}
