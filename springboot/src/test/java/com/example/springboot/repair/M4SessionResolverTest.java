package com.example.springboot.repair;

import com.example.springboot.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import static org.junit.jupiter.api.Assertions.*;

class M4SessionResolverTest {
    private final M4SessionResolver resolver = new M4SessionResolver();
    @Test void resolvesAllThreeActualAccountTypes() {
        Student student = new Student(); student.setUsername("StudentA"); student.setName("Same Name");
        DormManager staff = new DormManager(); staff.setUsername("staff1");
        Admin admin = new Admin(); admin.setUsername("admin");
        assertTrue(resolve("stu", student).isStudent());
        assertEquals("StudentA", resolve("stu", student).getUsername());
        assertEquals("staff1", resolve("dormManager", staff).getDisplayName());
        assertTrue(resolve("admin", admin).isAdmin());
    }
    @Test void rejectsMissingSessionWithoutCreatingOne() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals(401, assertThrows(M4ApiException.class, () -> resolver.resolve(request)).getStatus());
        assertNull(request.getSession(false));
    }
    @Test void rejectsMismatchedRoleAndTypeAndBlankAccounts() {
        Student student = new Student(); student.setUsername("student");
        assertThrows(M4ApiException.class, () -> resolve("admin", student));
        assertThrows(M4ApiException.class, () -> resolve("unknown", student));
        student.setUsername("  ");
        assertThrows(M4ApiException.class, () -> resolve("stu", student));
        assertThrows(M4ApiException.class, () -> resolve("stu", new User()));
    }
    private M4Actor resolve(String role, Object account) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("Identity", role); session.setAttribute("User", account);
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setSession(session);
        return resolver.resolve(request);
    }
}
