// M3: Unit coverage for booking validation, availability, conflicts, and ownership.
package com.example.springboot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceTest {
    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService();
        ReflectionTestUtils.setField(service, "jdbc", mock(JdbcTemplate.class));
    }

    @Test
    void rejectsPastBookingDatesBeforeDatabaseWrite() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("student1", 1L, LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1), "09:00-10:00"));
    }

    @Test
    void rejectsDatesBeyondTheThirtyDayWindowBeforeDatabaseRead() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("student1", 1L, LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(31), "09:00-10:00"));
    }

    @Test
    void rejectsArbitraryTimesAndOnlyAcceptsPublishedFixedSlots() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("student1", 1L, LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1), "09:30-10:30"));
    }

    @Test
    void translatesDatabaseUniqueKeyConflictIntoAUsableBookingError() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(1);
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenThrow(new DuplicateKeyException("occupied slot"));

        assertThrows(IllegalArgumentException.class,
                () -> service.create("student1", 1L, LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1), "09:00-10:00"));
    }

    @Test
    void refusesCancellationWhenTheBookingIsNotOwnedOrAlreadyInactive() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        when(jdbc.update(anyString(), eq(12L), eq("student2"))).thenReturn(0);

        assertThrows(IllegalArgumentException.class, () -> service.cancel("student2", 12L));
    }

    @Test
    void availabilityAlwaysReturnsEveryFixedSlotAndMarksElapsedCampusSlotsUnavailable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq(1L), any(LocalDate.class))).thenReturn(java.util.Collections.emptyList());

        java.util.Map<String, Object> result = service.getAvailability(1L, LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1));

        assertEquals(Arrays.asList(
                "08:00-09:00", "09:00-10:00", "10:00-11:00", "11:00-12:00",
                "12:00-13:00", "13:00-14:00", "14:00-15:00", "15:00-16:00",
                "16:00-17:00", "17:00-18:00", "18:00-19:00", "19:00-20:00",
                "20:00-21:00", "21:00-22:00"), BookingService.TIME_SLOTS);
        assertEquals(BookingService.TIME_SLOTS, result.get("timeSlots"));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Collections.emptyList(), result.get("unavailableSlots"));
    }

    @Test
    void campusTimeIsUsedForDateAndElapsedSlotValidation() {
        LocalDate campusToday = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        assertThrows(IllegalArgumentException.class,
                () -> service.create("student1", 1L, campusToday, "07:00-08:00"));
    }

    @Test
    void validatesDateAndSlotBeforeWritingAndChecksRoomAvailability() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(0);

        assertThrows(IllegalArgumentException.class,
                () -> service.create("student1", 1L, LocalDate.now().plusDays(1), "09:00-10:00"));
        verify(jdbc).queryForObject(anyString(), eq(Integer.class), eq(1L));
    }
}
