package com.estatecrm.mail_service.service;

import com.estatecrm.mail_service.dto.SendMailRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gui email qua Resend API (HTTPS) thay vi SMTP. Ly do: Render free chan outbound
 * port 25/465/587, con HTTPS thi khong, nen cach nay gui duoc mail ca tren free plan.
 *
 * API key lay tu RESEND_API_KEY (khong co default: thieu cau hinh thi service phai
 * fail ngay khi start, khong chay o che do "khong gui duoc mail" roi bao loi mu mu).
 * Loi cua nha cung cap duoc doi thanh 500 voi message chung: chi tiet loi (co the
 * chua ten mien/tai khoan) khong duoc tra ra client.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private static final String RESEND_ENDPOINT = "https://api.resend.com/emails";

    private final RestClient restClient;
    private final String from;

    public MailService(
            RestClient.Builder restClientBuilder,
            @Value("${app.mail.resend.api-key}") String apiKey,
            @Value("${app.mail.from}") String from) {
        this.restClient = restClientBuilder
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.from = from;
    }

    public void send(SendMailRequest request) {
        try {
            restClient.post()
                    .uri(RESEND_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResendEmail(from, List.of(request.to()), request.subject(), request.body()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            // Log lai ly do that bai (status + message cua Resend). Khong co dong nay thi
            // loi gui mail chi hien ra duoi dang 500 o service goi, khong biet vi sao.
            log.warn("Resend rejected the send request: {}", exception.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Email delivery failed");
        }
    }

    /** Payload dung format Resend: 'to' la mang, noi dung van ban nam o 'text'. */
    record ResendEmail(String from, List<String> to, String subject, String text) {
    }
}
