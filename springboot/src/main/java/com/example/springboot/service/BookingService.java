// M3: Room booking availability, conflict protection, cancellation, and time-window rules.
package com.example.springboot.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BookingService {
    // M3: Campus bookings run in consecutive one-hour slots from 08:00 to 22:00.
    public static final List<String> TIME_SLOTS = Arrays.asList(
            "08:00-09:00", "09:00-10:00", "10:00-11:00", "11:00-12:00",
            "12:00-13:00", "13:00-14:00", "14:00-15:00", "15:00-16:00",
            "16:00-17:00", "17:00-18:00", "18:00-19:00", "19:00-20:00",
            "20:00-21:00", "21:00-22:00");
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");
    @Resource
    private JdbcTemplate jdbc;

    public List<Map<String, Object>> listRooms() {
        return jdbc.queryForList("SELECT room_id AS roomId, room_code AS roomCode, room_name AS roomName, capacity FROM campus_room WHERE active = 1 ORDER BY room_code");
    }

    public Map<String, Object> getAvailability(Long roomId, LocalDate date) {
        validateDate(date);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM campus_room WHERE room_id = ? AND active = 1", Integer.class, roomId) == 0)
            throw new IllegalArgumentException("The selected room is unavailable.");
        List<Map<String, Object>> occupied = jdbc.queryForList(
                "SELECT time_slot FROM room_booking WHERE room_id = ? AND booking_date = ? AND status = 'BOOKED'", roomId, date);
        Map<String, Object> result = new HashMap<>();
        result.put("date", date.toString());
        result.put("timeSlots", TIME_SLOTS);
        result.put("bookedSlots", occupied);
        LocalTime now = LocalTime.now(CAMPUS_ZONE);
        List<String> unavailableSlots = date.equals(LocalDate.now(CAMPUS_ZONE))
                ? TIME_SLOTS.stream().filter(slot -> !now.isBefore(LocalTime.parse(slot.substring(0, 5))))
                    .collect(java.util.stream.Collectors.toList())
                : java.util.Collections.emptyList();
        result.put("unavailableSlots", unavailableSlots);
        return result;
    }

    @Transactional
    public Map<String, Object> create(String username, Long roomId, LocalDate date, String slot) {
        validateDate(date);
        if (!TIME_SLOTS.contains(slot)) throw new IllegalArgumentException("Choose one of the available fixed time slots.");
        if (date.equals(LocalDate.now(CAMPUS_ZONE)) && !LocalTime.now(CAMPUS_ZONE).isBefore(LocalTime.parse(slot.substring(0, 5))))
            throw new IllegalArgumentException("This time slot has already started.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM campus_room WHERE room_id = ? AND active = 1", Integer.class, roomId) == 0)
            throw new IllegalArgumentException("The selected room is unavailable.");
        try {
            jdbc.update("INSERT INTO room_booking(room_id, username, booking_date, time_slot, status) VALUES (?, ?, ?, ?, 'BOOKED')",
                    roomId, username, date, slot);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("That room and time slot has just been booked. Choose another slot.");
        }
        return jdbc.queryForMap("SELECT b.booking_id AS bookingId, b.room_id AS roomId, r.room_name AS roomName, b.booking_date AS date, b.time_slot AS timeSlot, b.status FROM room_booking b JOIN campus_room r ON r.room_id = b.room_id WHERE b.booking_id = LAST_INSERT_ID()");
    }

    public List<Map<String, Object>> listMine(String username) {
        // M3: Keep newly created bookings first, including bookings created in the same second.
        return jdbc.queryForList("SELECT b.booking_id AS bookingId, b.room_id AS roomId, r.room_name AS roomName, b.booking_date AS date, b.time_slot AS timeSlot, b.status, b.created_at AS createdAt, b.cancelled_at AS cancelledAt FROM room_booking b JOIN campus_room r ON r.room_id = b.room_id WHERE b.username = ? ORDER BY b.created_at DESC, b.booking_id DESC", username);
    }

    @Transactional
    public void cancel(String username, Long bookingId) {
        int changed = jdbc.update("UPDATE room_booking SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP WHERE booking_id = ? AND username = ? AND status = 'BOOKED'",
                bookingId, username);
        if (changed == 0) throw new IllegalArgumentException("Booking not found, already cancelled, or not owned by this account.");
    }

    private void validateDate(LocalDate date) {
        LocalDate today = LocalDate.now(CAMPUS_ZONE);
        if (date == null || date.isBefore(today) || date.isAfter(today.plusDays(30)))
            throw new IllegalArgumentException("Choose a date from today through the next 30 days.");
    }
}
