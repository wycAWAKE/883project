package com.example.springboot.repair;

import com.example.springboot.entity.Admin;
import com.example.springboot.entity.DormManager;
import com.example.springboot.entity.Student;
import org.springframework.stereotype.Component;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@Component
public class M4SessionResolver {
    public M4Actor resolve(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) throw unauthorized();
        Object role = session.getAttribute("Identity");
        Object user = session.getAttribute("User");
        String username;
        String name;
        if ("stu".equals(role) && user instanceof Student) {
            username = ((Student) user).getUsername();
            name = ((Student) user).getName();
        } else if ("dormManager".equals(role) && user instanceof DormManager) {
            username = ((DormManager) user).getUsername();
            name = ((DormManager) user).getName();
        } else if ("admin".equals(role) && user instanceof Admin) {
            username = ((Admin) user).getUsername();
            name = ((Admin) user).getName();
        } else {
            throw unauthorized();
        }
        if (username == null || username.trim().isEmpty() || username.length() > 64) {
            throw unauthorized();
        }
        // Preserve the canonical account key, including case. Names are display only.
        return new M4Actor((String) role, username,
                name == null || name.trim().isEmpty() ? username : name);
    }
    private M4ApiException unauthorized() {
        return new M4ApiException(401, "Please sign in again.");
    }
}
