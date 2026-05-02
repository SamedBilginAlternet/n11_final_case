package com.n11.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class TrLocationCatalogTest {

    private static TrLocationCatalog catalog;

    @BeforeAll
    static void setUp() throws Exception {
        catalog = new TrLocationCatalog(new ObjectMapper());
        // @PostConstruct isn't fired outside Spring; invoke load() directly.
        Method m = TrLocationCatalog.class.getDeclaredMethod("load");
        m.setAccessible(true);
        m.invoke(catalog);
    }

    @Test
    void recognisesAllSpellingsOfIstanbul() {
        assertThat(catalog.isValidCity("İstanbul")).isTrue();
        assertThat(catalog.isValidCity("Istanbul")).isTrue();   // dotless I
        assertThat(catalog.isValidCity("ISTANBUL")).isTrue();
        assertThat(catalog.isValidCity("istanbul")).isTrue();
        assertThat(catalog.isValidCity("  İstanbul  ")).isTrue();
    }

    @Test
    void rejectsUnknownCity() {
        assertThat(catalog.isValidCity("Atlantis")).isFalse();
        assertThat(catalog.isValidCity("")).isFalse();
        assertThat(catalog.isValidCity(null)).isFalse();
    }

    @Test
    void districtMustBelongToCity() {
        assertThat(catalog.isValidDistrict("İstanbul", "Kadıköy")).isTrue();
        assertThat(catalog.isValidDistrict("Istanbul", "Kadikoy")).isTrue();   // diacritic-free
        assertThat(catalog.isValidDistrict("Ankara", "Çankaya")).isTrue();
        // Üsküdar belongs to İstanbul, not Ankara
        assertThat(catalog.isValidDistrict("Ankara", "Üsküdar")).isFalse();
    }

    @Test
    void blankDistrictIsAllowed() {
        // District is optional in the schema; only city is required.
        assertThat(catalog.isValidDistrict("İstanbul", null)).isTrue();
        assertThat(catalog.isValidDistrict("İstanbul", "")).isTrue();
    }
}
