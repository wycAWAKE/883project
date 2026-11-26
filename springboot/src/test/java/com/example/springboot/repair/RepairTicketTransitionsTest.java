package com.example.springboot.repair;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RepairTicketTransitionsTest {
    @Test void acceptsOnlyTheThreeLinearStatusChanges() {
        assertEquals("STARTED", RepairTicketTransitions.action("ASSIGNED", "IN_PROGRESS"));
        assertEquals("RESOLVED", RepairTicketTransitions.action("IN_PROGRESS", "RESOLVED"));
        assertEquals("CLOSED", RepairTicketTransitions.action("RESOLVED", "CLOSED"));
        for (RepairTicketStatus from : RepairTicketStatus.values()) {
            for (RepairTicketStatus to : RepairTicketStatus.values()) {
                if ((from == RepairTicketStatus.ASSIGNED && to == RepairTicketStatus.IN_PROGRESS)
                        || (from == RepairTicketStatus.IN_PROGRESS && to == RepairTicketStatus.RESOLVED)
                        || (from == RepairTicketStatus.RESOLVED && to == RepairTicketStatus.CLOSED)) continue;
                assertEquals(409, assertThrows(M4ApiException.class,
                        () -> RepairTicketTransitions.action(from.name(), to.name())).getStatus());
            }
        }
    }
    @Test void assignmentAndProgressHaveSeparateStateRules() {
        RepairTicketTransitions.requireAssignable("SUBMITTED");
        RepairTicketTransitions.requireAssignable("ASSIGNED");
        RepairTicketTransitions.requireInProgress("IN_PROGRESS");
        assertThrows(M4ApiException.class, () -> RepairTicketTransitions.requireAssignable("IN_PROGRESS"));
        assertThrows(M4ApiException.class, () -> RepairTicketTransitions.requireInProgress("CLOSED"));
    }
    @Test void invalidTargetStatusIsAnInputError() {
        for (String target : new String[]{null,"", "UNKNOWN"}) {
            assertEquals(400, assertThrows(M4ApiException.class,
                    () -> RepairTicketTransitions.action("ASSIGNED", target)).getStatus());
        }
    }
}
