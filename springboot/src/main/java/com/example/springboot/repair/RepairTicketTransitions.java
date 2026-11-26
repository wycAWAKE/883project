package com.example.springboot.repair;

public final class RepairTicketTransitions {
    private RepairTicketTransitions() { }
    public static void requireAssignable(String current) {
        if (!"SUBMITTED".equals(current) && !"ASSIGNED".equals(current)) {
            throw new M4ApiException(409, "Only tickets awaiting work can be assigned.");
        }
    }
    public static String action(String current, String requested) {
        String target = RepairTicketStatus.validateFilter(requested);
        if (target == null) throw M4ApiException.badRequest("A target status is required.");
        if ("ASSIGNED".equals(current) && "IN_PROGRESS".equals(target)) return "STARTED";
        if ("IN_PROGRESS".equals(current) && "RESOLVED".equals(target)) return "RESOLVED";
        if ("RESOLVED".equals(current) && "CLOSED".equals(target)) return "CLOSED";
        throw new M4ApiException(409, "The requested status transition is not allowed.");
    }
    public static void requireInProgress(String current) {
        if (!"IN_PROGRESS".equals(current)) throw new M4ApiException(409, "Progress can only be added while work is in progress.");
    }
}
