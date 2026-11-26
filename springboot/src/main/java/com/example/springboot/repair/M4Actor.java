package com.example.springboot.repair;

import lombok.Getter;

@Getter
public final class M4Actor {
    private final String role;
    private final String username;
    private final String displayName;
    M4Actor(String role, String username, String displayName) {
        this.role = role;
        this.username = username;
        this.displayName = displayName;
    }
    public boolean isStudent() { return "stu".equals(role); }
    public boolean isStaff() { return "dormManager".equals(role); }
    public boolean isAdmin() { return "admin".equals(role); }
}
