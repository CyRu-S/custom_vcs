package com.vcs.customvcs.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.*;

// Password hashes stay in persistence and are never returned by a controller.
@Entity
@Table(name = "app_users")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 80) private String name;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
