package com.example.springboot.repair;

public enum RepairLocationType {
    DORM, CLASSROOM, PUBLIC_AREA, OTHER;
    public static String validate(String value) {
        if (value != null) {
            try { return valueOf(value.trim()).name(); }
            catch (IllegalArgumentException ignored) { }
        }
        throw M4ApiException.badRequest("Choose a valid location type.");
    }
}
