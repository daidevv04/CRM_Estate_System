package com.estatecrm.mail_service.service;

import com.estatecrm.mail_service.dto.SendMailRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
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
            String rawBody = request.body();
            boolean isHtml = (request.html() != null && !request.html().isBlank())
                    || (rawBody != null && (rawBody.contains("<p") || rawBody.contains("<div")
                            || rawBody.contains("<br") || rawBody.contains("<table") || rawBody.contains("<html")));

            String htmlContent = (request.html() != null && !request.html().isBlank())
                    ? request.html()
                    : isHtml ? rawBody : null;

            String textContent = isHtml ? stripHtml(rawBody) : rawBody;

            String senderFrom = this.from;
            if (request.senderName() != null && !request.senderName().isBlank()) {
                String emailAddress = extractEmail(this.from);
                senderFrom = request.senderName().trim() + " <" + emailAddress + ">";
            }

            List<String> replyToList = (request.replyTo() != null && !request.replyTo().isBlank())
                    ? List.of(request.replyTo().trim())
                    : null;

            ResendEmail payload = new ResendEmail(
                    senderFrom,
                    List.of(request.to()),
                    request.subject(),
                    textContent,
                    htmlContent,
                    replyToList);

            restClient.post()
                    .uri(RESEND_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Email đã gửi thành công tới {} qua Resend (from={}, replyTo={})",
                    request.to(), senderFrom, replyToList);
        } catch (RestClientException exception) {
            log.warn("Resend rejected the send request to {}: {}", request.to(), exception.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Email delivery failed: " + exception.getMessage());
        }
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n\n")
                .replaceAll("(?i)</div>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }

    private String extractEmail(String fromStr) {
        if (fromStr == null) return "no-reply@daidev.me";
        int start = fromStr.indexOf('<');
        int end = fromStr.indexOf('>', start);
        if (start >= 0 && end > start) {
            return fromStr.substring(start + 1, end).trim();
        }
        return fromStr.trim();
    }

    record ResendEmail(
            String from,
            List<String> to,
            String subject,
            String text,
            String html,
            @JsonProperty("reply_to") List<String> replyTo) {
    }
}
