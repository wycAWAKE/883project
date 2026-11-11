package com.example.springboot.repair;

public final class M4Input {
    private M4Input() { }
    public static String strip(String value) {
        if (value == null) return "";
        int start = 0, end = value.length();
        while (start < end && blank(value.codePointAt(start))) start += Character.charCount(value.codePointAt(start));
        while (end > start && blank(value.codePointBefore(end))) end -= Character.charCount(value.codePointBefore(end));
        return value.substring(start, end);
    }
    private static boolean blank(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
    public static String optional(String value, int max, String field) {
        String result = strip(value);
        if (result.length() > max) throw M4ApiException.badRequest(field+" must not exceed "+max+" characters.");
        return result;
    }
    public static String required(String value, int max, String field) {
        String result = optional(value, max, field);
        if (result.isEmpty()) throw M4ApiException.badRequest(field+" is required.");
        return result;
    }
    public static int version(Integer value) {
        if (value == null || value < 0 || value == Integer.MAX_VALUE) {
            throw M4ApiException.badRequest("A non-negative, incrementable integer version is required.");
        }
        return value;
    }
}
