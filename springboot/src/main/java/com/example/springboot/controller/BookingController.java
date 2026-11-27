// M3: Session-protected room booking API endpoints.
package com.example.springboot.controller;

import com.example.springboot.common.Result;
import com.example.springboot.service.BookingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/bookings")
public class BookingController {
    @Resource
    private BookingService bookingService;

    @GetMapping("/rooms")
    public Result<?> rooms(HttpSession session) {
        if (username(session) == null) return Result.error("-1", "Please sign in to view rooms.");
        return Result.success(bookingService.listRooms());
    }

    @GetMapping("/availability")
    public Result<?> availability(@RequestParam Long roomId, @RequestParam String date, HttpSession session) {
        if (username(session) == null) return Result.error("-1", "Please sign in to view availability.");
        try {
            return Result.success(bookingService.getAvailability(roomId, LocalDate.parse(date)));
        } catch (DateTimeException | IllegalArgumentException e) {
            return Result.error("-1", e.getMessage());
        }
    }

    @GetMapping("/mine")
    public Result<?> mine(HttpSession session) {
        String username = username(session);
        return username == null ? Result.error("-1", "Please sign in to view bookings.")
                : Result.success(bookingService.listMine(username));
    }

    @PostMapping
    public Result<?> create(@RequestBody Map<String, Object> request, HttpSession session) {
        String username = username(session);
        if (username == null) return Result.error("-1", "Please sign in to book a room.");
        try {
            if (request == null) throw new IllegalArgumentException("A room, date, and time slot are required.");
            Long roomId = Long.valueOf(String.valueOf(request.get("roomId")));
            LocalDate date = LocalDate.parse(String.valueOf(request.get("date")));
            String slot = String.valueOf(request.get("timeSlot"));
            return Result.success(bookingService.create(username, roomId, date, slot));
        } catch (DateTimeException | IllegalArgumentException e) {
            return Result.error("-1", e.getMessage());
        }
    }

    @PostMapping("/{bookingId}/cancel")
    public Result<?> cancel(@PathVariable Long bookingId, HttpSession session) {
        String username = username(session);
        if (username == null) return Result.error("-1", "Please sign in to cancel a booking.");
        try {
            bookingService.cancel(username, bookingId);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error("-1", e.getMessage());
        }
    }

    private String username(HttpSession session) {
        Object identity = session.getAttribute("Identity");
        Object user = session.getAttribute("User");
        if (!("stu".equals(identity) || "dormManager".equals(identity) || "admin".equals(identity)) || user == null) return null;
        try {
            Object value = user.getClass().getMethod("getUsername").invoke(user);
            return value instanceof String && !((String) value).trim().isEmpty() ? (String) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
