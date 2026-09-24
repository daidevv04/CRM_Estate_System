package com.estatecrm.config_service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * Kiem tra config-server thuc su phuc vu duoc file trong config-repo va khong
 * mo cong khai: day la hai dieu kien song con, vi response chua cau hinh moi
 * truong cua toan he thong.
 *
 * Goi bang HttpClient cua JDK: TestRestTemplate khong con trong starter test
 * cua Spring Boot 4, va mot client HTTP that cung kiem tra duoc basic auth.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=native",
            "spring.cloud.config.server.native.search-locations=classpath:/test-config-repo",
            "spring.cloud.consul.enabled=false",
            "spring.cloud.consul.discovery.enabled=false",
            "CONFIG_SERVER_USERNAME=test",
            "CONFIG_SERVER_PASSWORD=test"
        })
class ConfigServerServesPropertiesTests {

    private static final String USER_SERVICE = "/user-service/default";

    @Autowired
    Environment environment;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, boolean authenticated) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + environment.getProperty("local.server.port") + path))
                .GET();
        if (authenticated) {
            String credentials = Base64.getEncoder()
                    .encodeToString("test:test".getBytes(StandardCharsets.UTF_8));
            request.header("Authorization", "Basic " + credentials);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void servesPropertiesFromConfigRepo() throws Exception {
        HttpResponse<String> response = get(USER_SERVICE, true);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("app.auth.otp-ttl").contains("7m");
    }

    @Test
    void rejectsRequestWithoutCredentials() throws Exception {
        HttpResponse<String> response = get(USER_SERVICE, false);

        assertThat(response.statusCode()).isEqualTo(401);
    }
}
