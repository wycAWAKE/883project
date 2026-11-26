// M3: Real MySQL race and cancellation/rebooking verification.
package com.example.springboot.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class BookingServiceMySqlIntegrationTest {
    @Resource
    private BookingService bookingService;

    @Resource
    private JdbcTemplate jdbc;

    @Test
    void concurrentBookingsHaveOneWinnerAndCancellationReleasesTheSlot() throws Exception {
        String username = "m3-race-" + UUID.randomUUID().toString().substring(0, 8);
        Long roomId = ((Number) bookingService.listRooms().get(0).get("roomId")).longValue();
        LocalDate date = null;
        String slot = null;
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

        for (int day = 1; day <= 30 && date == null; day++) {
            LocalDate candidate = today.plusDays(day);
            Map<String, Object> availability = bookingService.getAvailability(roomId, candidate);
            for (String candidateSlot : BookingService.TIME_SLOTS) {
                boolean booked = ((List<Map<String, Object>>) availability.get("bookedSlots")).stream()
                        .anyMatch(row -> candidateSlot.equals(row.get("time_slot")));
                if (!booked) {
                    date = candidate;
                    slot = candidateSlot;
                    break;
                }
            }
        }
        assertNotNull(date, "The M3 database must have at least one free slot in the next 30 days");
        assertNotNull(slot);

        ExecutorService writers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        LocalDate bookingDate = date;
        String bookingSlot = slot;
        try {
            Future<Boolean> first = writers.submit(() -> tryBook(username, roomId, bookingDate, bookingSlot, ready, start));
            Future<Boolean> second = writers.submit(() -> tryBook(username, roomId, bookingDate, bookingSlot, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Both writers should be ready before the race starts");
            start.countDown();

            int successes = (first.get(10, TimeUnit.SECONDS) ? 1 : 0) + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes, "MySQL should allow exactly one active booking for the room, date, and slot");
            List<Map<String, Object>> history = bookingService.listMine(username);
            assertEquals(1, history.size());
            Long firstBookingId = ((Number) history.get(0).get("bookingId")).longValue();

            bookingService.cancel(username, firstBookingId);
            bookingService.create(username, roomId, bookingDate, bookingSlot);

            List<Map<String, Object>> statuses = jdbc.queryForList(
                    "SELECT status FROM room_booking WHERE username = ? ORDER BY booking_id", username);
            assertEquals(2, statuses.size());
            assertEquals("CANCELLED", statuses.get(0).get("status"));
            assertEquals("BOOKED", statuses.get(1).get("status"));
        } finally {
            start.countDown();
            writers.shutdownNow();
            jdbc.update("UPDATE room_booking SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP WHERE username = ? AND status = 'BOOKED'", username);
        }
    }

    private boolean tryBook(String username, Long roomId, LocalDate date, String slot,
                            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            bookingService.create(username, roomId, date, slot);
            return true;
        } catch (IllegalArgumentException conflict) {
            return false;
        }
    }
}
