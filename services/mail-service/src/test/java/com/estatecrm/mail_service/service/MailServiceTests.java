package com.estatecrm.mail_service.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.estatecrm.mail_service.dto.SendMailRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Kiem tra payload gui Resend va cach doi loi nha cung cap. Dung
 * MockRestServiceServer de khong goi API that: test van chay khi khong co mang
 * va khong tieu quota.
 */
class MailServiceTests {

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final MailService mailService =
            new MailService(restClientBuilder, "re_test_key", "crm@estate.local");

    @Test
    void sendsMessageThroughResendWithBearerKey() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_key"))
                .andExpect(jsonPath("$.from").value("crm@estate.local"))
                .andExpect(jsonPath("$.to[0]").value("sales@estate.local"))
                .andExpect(jsonPath("$.subject").value("Ma OTP"))
                .andExpect(jsonPath("$.text").value("Ma cua ban la 123456"))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

        mailService.send(new SendMailRequest("sales@estate.local", "Ma OTP", "Ma cua ban la 123456"));

        server.verify();
    }

    @Test
    void providerFailureBecomesInternalErrorWithoutLeakingDetails() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("{\"message\":\"Domain not verified for sales@estate.local\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> mailService.send(new SendMailRequest("sales@estate.local", "s", "b")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Email delivery failed");
    }
}
