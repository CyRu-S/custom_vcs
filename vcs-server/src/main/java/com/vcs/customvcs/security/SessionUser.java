package com.vcs.customvcs.security;

import java.io.Serializable;
import java.util.UUID;

// A session contains public identity only, never the password or its hash.
public record SessionUser(UUID id, String name, String email) implements Serializable {
}
