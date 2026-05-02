package com.n11.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * In-memory catalog of Turkey's 81 provinces and their districts, sourced from
 * {@code tr-locations.json} on the classpath.  Used by {@link TrLocationValidator}
 * to enforce that user-entered addresses pick a valid (city, district) pair.
 *
 * <p>Lookup is normalised: input is lowercased with Turkish-aware rules
 * ({@code I → ı}, {@code İ → i}) before matching so "ISTANBUL", "İstanbul",
 * "istanbul" all hit the same row.</p>
 *
 * <p>Loaded once on startup; data is small (~22 KB, 81 cities, ~970 districts)
 * so we keep the whole index in maps for O(1) lookups in the request path.</p>
 */
@Component
@Slf4j
public class TrLocationCatalog {

    public record Location(String city, List<String> districts) {}

    /**
     * Diacritic-insensitive folding so users can type "Istanbul" (no İ),
     * "ISTANBUL", "istanbul", "İstanbul" — all match the same row.  We map
     * Turkish-specific letters straight to their ASCII counterparts; the
     * normalised key is what we store and what we look up.
     */
    private static final Map<Character, Character> ASCII_FOLD = Map.ofEntries(
            Map.entry('ı', 'i'), Map.entry('İ', 'i'),
            Map.entry('ş', 's'), Map.entry('Ş', 's'),
            Map.entry('ğ', 'g'), Map.entry('Ğ', 'g'),
            Map.entry('ü', 'u'), Map.entry('Ü', 'u'),
            Map.entry('ö', 'o'), Map.entry('Ö', 'o'),
            Map.entry('ç', 'c'), Map.entry('Ç', 'c'));

    private final ObjectMapper objectMapper;

    /** Lower-cased city name → canonical city name as displayed in dropdowns. */
    private final Map<String, String> cityIndex = new HashMap<>();
    /** Lower-cased city name → set of lower-cased valid districts. */
    private final Map<String, Set<String>> districtIndex = new HashMap<>();

    public TrLocationCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() throws Exception {
        try (InputStream in = new ClassPathResource("tr-locations.json").getInputStream()) {
            List<Location> rows = objectMapper.readValue(in, new TypeReference<>() {});
            for (Location row : rows) {
                String cityKey = normalise(row.city());
                cityIndex.put(cityKey, row.city());
                Set<String> set = new TreeSet<>();
                for (String d : row.districts()) {
                    set.add(normalise(d));
                }
                districtIndex.put(cityKey, set);
            }
        }
        log.info("Loaded TR locations: {} cities, {} districts",
                cityIndex.size(),
                districtIndex.values().stream().mapToInt(Set::size).sum());
    }

    public boolean isValidCity(String city) {
        if (city == null || city.isBlank()) return false;
        return cityIndex.containsKey(normalise(city));
    }

    /** District is optional in our model — null/blank passes here, real form-level
     *  enforcement is via {@code @NotBlank} on the request DTO if desired. */
    public boolean isValidDistrict(String city, String district) {
        if (district == null || district.isBlank()) return true;
        if (city == null || city.isBlank()) return false;
        Set<String> set = districtIndex.get(normalise(city));
        return set != null && set.contains(normalise(district));
    }

    private static String normalise(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.trim().toCharArray()) {
            sb.append(ASCII_FOLD.getOrDefault(c, c));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
