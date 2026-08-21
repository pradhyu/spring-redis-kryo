package com.example.kryo.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class UserProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String email;
    private boolean active;
    private List<String> roles;
    private Map<String, String> preferences;
    private Instant createdAt;

    public UserProfile() {
    }

    public UserProfile(Long id, String username, String email, boolean active,
                       List<String> roles, Map<String, String> preferences, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.active = active;
        this.roles = roles;
        this.preferences = preferences;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public Map<String, String> getPreferences() {
        return preferences;
    }

    public void setPreferences(Map<String, String> preferences) {
        this.preferences = preferences;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserProfile that)) return false;
        return active == that.active &&
                Objects.equals(id, that.id) &&
                Objects.equals(username, that.username) &&
                Objects.equals(email, that.email) &&
                Objects.equals(roles, that.roles) &&
                Objects.equals(preferences, that.preferences) &&
                Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username, email, active, roles, preferences, createdAt);
    }

    @Override
    public String toString() {
        return "UserProfile{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", active=" + active +
                ", roles=" + roles +
                ", preferences=" + preferences +
                ", createdAt=" + createdAt +
                '}';
    }
}
