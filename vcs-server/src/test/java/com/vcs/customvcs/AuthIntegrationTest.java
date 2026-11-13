package com.vcs.customvcs;

import java.nio.file.Path;
import java.util.UUID;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import com.vcs.customvcs.repository.UserRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Use the real filter chain, password hashing, migrations, JPA, and temporary repository storage.
@SpringBootTest(properties = { "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:auth-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver" })
class AuthIntegrationTest {
    @TempDir static Path storage;
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    MockMvc mvc;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("customvcs.storage.root", () -> storage.toString());
    }
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test void anonymousRequestsAndMissingCsrfAreRejected() throws Exception {
        mvc.perform(get("/api/repositories")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody(email())))
                .andExpect(status().isForbidden());
    }

    @Test void realCsrfTokenRotatesSessionAndLogoutEndsAuthentication() throws Exception {
        var tokenResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.token");
        String header = JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.headerName");
        MockHttpSession before = (MockHttpSession) tokenResult.getRequest().getSession(false);
        String originalId = before.getId();
        var registered = mvc.perform(post("/api/auth/register").session(before).header(header, token)
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody(email())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);
        assertNotEquals(originalId, session.getId());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        // The pre-login CSRF token no longer authorizes mutations after authentication.
        mvc.perform(post("/api/repositories").session(session).header(header, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"old-token\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void passwordsAreHashedAndLoginErrorsAreGeneric() throws Exception {
        String email = email(); register(email);
        String hash = users.findByEmail(email).orElseThrow().getPasswordHash();
        assertNotEquals("test-password", hash); assertTrue(hash.startsWith("$2"));
        for (String address : new String[] { email, email() }) {
            mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + address + "\",\"password\":\"incorrect\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Email or password is incorrect."));
        }
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk());
    }

    @Test void anotherAccountCannotReadMutateOrListPrivateRepositories() throws Exception {
        MockHttpSession owner = register(email()), other = register(email());
        var created = mvc.perform(post("/api/repositories").session(owner).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"private\"}"))
                .andExpect(status().isCreated()).andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/repositories").session(other)).andExpect(jsonPath("$.totalElements").value(0));
        for (String suffix : new String[] { "", "/files", "/status", "/commits", "/workspace", "/branches", "/merge" }) {
            mvc.perform(get("/api/repositories/" + id + suffix).session(other)).andExpect(status().isNotFound());
        }
        mvc.perform(delete("/api/repositories/" + id).session(other).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/repositories/" + id + "/stage").session(other).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"x.txt\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/repositories/" + id).session(owner)).andExpect(status().isOk());
    }

    @Test void duplicateRegistrationAndInvalidFieldsReturnUsefulErrors() throws Exception {
        String email = email(); register(email);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(registerBody(email)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"invalid\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.password").exists());
    }

    private MockHttpSession register(String email) throws Exception {
        var result = mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email))).andExpect(status().isCreated()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
    private String email() { return UUID.randomUUID() + "@example.test"; }
    private String registerBody(String email) { return "{\"name\":\"Test Author\",\"email\":\"" + email + "\",\"password\":\"test-password\"}"; }
}
