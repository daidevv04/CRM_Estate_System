package com.estatecrm.crm_service.service;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);
    private final RestClient restClient;

    public UserClient(@Value("${app.user.base-url:http://localhost:8091}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public record StaffInfo(UUID id, String username, String fullName, String role) {}

    public List<StaffInfo> getSalesStaff() {
        try {
            List<StaffInfo> result = restClient.get()
                    .uri("/users/internal/sales-staff")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<StaffInfo>>() {});
            if (result != null) {
                return result;
            }
        } catch (Exception ex) {
            log.warn("Không thể lấy danh sách sales từ user-service: {}", ex.getMessage());
        }
        return List.of();
    }
}
