package com.n11.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class ProductCatalogIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("productdb").withUsername("test").withPassword("test");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void listsCategories() throws Exception {
        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.slug=='elektronik')]").exists());
    }

    @Test
    void paginatesProductsAndIncludesPageMetadata() throws Exception {
        String body = mvc.perform(get("/api/products?page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.totalElements").value(13))
                .andExpect(jsonPath("$.first").value(true))
                .andReturn().getResponse().getContentAsString();

        JsonNode root = mapper.readTree(body);
        assertThat(root.get("totalPages").asInt()).isGreaterThan(1);
    }

    @Test
    void filtersByCategorySlug() throws Exception {
        mvc.perform(get("/api/products?category=elektronik&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void searchesByQuery() throws Exception {
        mvc.perform(get("/api/products?q=iphone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].slug").value("iphone-15-pro-256gb"));
    }

    @Test
    void getsProductBySlug() throws Exception {
        mvc.perform(get("/api/products/slug/atomic-habits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Atomic Habits"))
                .andExpect(jsonPath("$.categorySlug").value("kitap"));
    }

    @Test
    void returns404ForUnknownSlug() throws Exception {
        mvc.perform(get("/api/products/slug/nope")).andExpect(status().isNotFound());
    }
}
