package com.n11.auth.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class GitHubEmailAwareUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String EMAILS_ENDPOINT = "https://api.github.com/user/emails";
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final RestTemplate rest = new RestTemplate();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(userRequest);
        if (!"github".equals(userRequest.getClientRegistration().getRegistrationId())) {
            return user;
        }

        Map<String, Object> attributes = new HashMap<>(user.getAttributes());
        if (attributes.get("email") == null) {
            String email = fetchPrimaryEmail(userRequest.getAccessToken().getTokenValue());
            if (email != null) {
                attributes.put("email", email);
            } else {
                throw new OAuth2AuthenticationException(new OAuth2Error(
                        "github_email_unavailable",
                        "Couldn't read a verified email from GitHub — check that user:email scope is granted",
                        null));
            }
        }
        String nameAttribute = user.getAttribute("login") != null ? "login" : "id";
        return new DefaultOAuth2User(user.getAuthorities(), attributes, nameAttribute);
    }

    private String fetchPrimaryEmail(String bearerToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(bearerToken);
            headers.add(HttpHeaders.ACCEPT, "application/vnd.github+json");
            RequestEntity<Void> req = new RequestEntity<>(headers, HttpMethod.GET, URI.create(EMAILS_ENDPOINT));
            List<Map<String, Object>> emails = rest.exchange(req, new ParameterizedTypeReference<List<Map<String, Object>>>() {}).getBody();
            if (emails == null) return null;
            return emails.stream()
                    .filter(e -> Boolean.TRUE.equals(e.get("primary")) && Boolean.TRUE.equals(e.get("verified")))
                    .map(e -> (String) e.get("email"))
                    .findFirst()
                    .orElse(null);
        } catch (Exception ex) {
            log.warn("Failed to fetch GitHub /user/emails: {}", ex.getMessage());
            return null;
        }
    }
}
