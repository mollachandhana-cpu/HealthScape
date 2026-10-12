package com.healthscapeai;

import jakarta.persistence.*;

@Entity @Table(name = "app_user")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(unique = true) public String username;
    public String password;
    public String role;   // ADMIN or WORKER
    protected AppUser() {}
    AppUser(String u, String p, String role) { username = u; password = p; this.role = role; }
}
