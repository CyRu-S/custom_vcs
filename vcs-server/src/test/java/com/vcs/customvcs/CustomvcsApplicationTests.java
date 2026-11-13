package com.vcs.customvcs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.vcs.customvcs.dto.CreateRepositoryRequest;
import com.vcs.customvcs.dto.RenameRepositoryRequest;
import com.vcs.customvcs.entity.RepositoryStatus;
import com.vcs.customvcs.service.RepositoryService;
import static org.junit.jupiter.api.Assertions.*;

// Run Flyway, Hibernate validation, and repository queries against disposable test data.
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:backend-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class CustomvcsApplicationTests {
    @TempDir static Path storage;
    @Autowired RepositoryService repositories;
    @Autowired com.vcs.customvcs.service.AuthService auth;

    @org.junit.jupiter.api.BeforeEach
    void signIn() {
        var user = auth.register(new com.vcs.customvcs.dto.RegisterRequest("Database Test",
                UUID.randomUUID() + "@example.test", "test-password"));
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated(user, null, java.util.List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
    }

    @org.junit.jupiter.api.AfterEach
    void signOut() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("customvcs.storage.root", () -> storage.toString());
    }

    @Test
    void contextLoads() {
    }

    @Test
    void migrationsAndJpaSupportTheRepositoryLifecycle() throws Exception {
        var created = repositories.create(new CreateRepositoryRequest("database-test"));
        UUID id = created.id();
        assertEquals(RepositoryStatus.READY, repositories.findById(id).orElseThrow().status());
        assertTrue(repositories.list(PageRequest.of(0, 20)).getContent().stream()
                .anyMatch(repository -> repository.id().equals(id)));
        assertEquals("renamed", repositories.rename(id, new RenameRepositoryRequest("renamed")).name());
        repositories.delete(id);
        assertTrue(repositories.findById(id).isEmpty());
        assertFalse(Files.exists(storage.resolve(id.toString())));
    }
}
