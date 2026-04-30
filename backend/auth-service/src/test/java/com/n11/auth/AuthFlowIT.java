package com.n11.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.auth.api.dto.LoginRequest;
import com.n11.auth.api.dto.RegisterRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AuthFlowIT {

    private static final String COOKIE_NAME = "n11_refresh";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("authdb").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void overrideJwt(DynamicPropertyRegistry registry) {
        registry.add("n11.jwt.secret", () -> "integration-test-32-byte-secret-padding-1234");
        // Tests run over plain HTTP — Secure would make the cookie unusable.
        registry.add("n11.auth.cookie.secure", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void registerThenLoginThenMe() throws Exception {
        var register = new RegisterRequest("it@n11.local", "verysecret", "Integration User");
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("it@n11.local"));

        MvcResult login = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest("it@n11.local", "verysecret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(notNullValue()))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().exists(COOKIE_NAME))
                .andExpect(cookie().httpOnly(COOKIE_NAME, true))
                .andExpect(cookie().path(COOKIE_NAME, "/api/auth"))
                .andReturn();

        JsonNode body = mapper.readTree(login.getResponse().getContentAsString());
        String token = body.get("accessToken").asText();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("it@n11.local"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void refreshRotatesAndReuseDetectionRevokes() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new RegisterRequest(
                                "rotate@n11.local", "verysecret", "Rotate User"))))
                .andExpect(status().isCreated());

        Cookie firstRefresh = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest("rotate@n11.local", "verysecret"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(COOKIE_NAME);
        assertThat(firstRefresh).isNotNull();

        // First rotation should succeed and return a fresh cookie.
        Cookie secondRefresh = mvc.perform(post("/api/auth/refresh").cookie(firstRefresh))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(COOKIE_NAME))
                .andReturn().getResponse().getCookie(COOKIE_NAME);
        assertThat(secondRefresh).isNotNull();
        assertThat(secondRefresh.getValue()).isNotEqualTo(firstRefresh.getValue());

        // Replaying the OLD (already-revoked) refresh must be rejected AND
        // must invalidate the rotated one too — the family is compromised.
        mvc.perform(post("/api/auth/refresh").cookie(firstRefresh))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(secondRefresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieRejected() throws Exception {
        mvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsCookieAndRevokesToken() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new RegisterRequest(
                                "logout@n11.local", "verysecret", "Logout User"))))
                .andExpect(status().isCreated());

        Cookie refresh = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest("logout@n11.local", "verysecret"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(COOKIE_NAME);

        mvc.perform(post("/api/auth/logout").cookie(refresh))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(COOKIE_NAME, 0));

        // The revoked refresh must no longer rotate.
        mvc.perform(post("/api/auth/refresh").cookie(refresh))
                .andExpect(status().isUnauthorized());
    }
}
