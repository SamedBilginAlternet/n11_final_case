package com.n11.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.auth.api.dto.LoginRequest;
import com.n11.auth.api.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AuthFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("authdb").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void overrideJwt(DynamicPropertyRegistry registry) {
        registry.add("n11.jwt.secret", () -> "integration-test-32-byte-secret-padding-1234");
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

        String loginBody = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest("it@n11.local", "verysecret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(notNullValue()))
                .andReturn().getResponse().getContentAsString();

        JsonNode body = mapper.readTree(loginBody);
        String token = body.get("accessToken").asText();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("it@n11.local"))
                .andExpect(jsonPath("$.role").value("USER"));
    }
}
