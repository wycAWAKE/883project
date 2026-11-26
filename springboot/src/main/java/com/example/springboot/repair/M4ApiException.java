package com.example.springboot.repair;

public class M4ApiException extends RuntimeException {
    private final int status;
    public M4ApiException(int status, String message) {
        super(message);
        this.status = status;
    }
    public int getStatus() { return status; }
    public static M4ApiException badRequest(String message) {
        return new M4ApiException(400, message);
    }
    public static M4ApiException notFound() {
        return new M4ApiException(404, "Ticket not found.");
    }
}
