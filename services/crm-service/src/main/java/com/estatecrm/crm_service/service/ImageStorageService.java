package com.estatecrm.crm_service.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Upload cover public vao Supabase Storage. Service role key chi nam o server. */
@Service
public class ImageStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final String BUCKET = "estate-media";
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String supabaseUrl;
    private final String serviceRoleKey;

    public ImageStorageService(
            @Value("${app.supabase.url:}") String supabaseUrl,
            @Value("${app.supabase.service-role-key:}") String serviceRoleKey) {
        this.supabaseUrl = stripTrailingSlash(supabaseUrl);
        this.serviceRoleKey = serviceRoleKey;
    }

    public String upload(String folder, String id, MultipartFile file) {
        String contentType = file.getContentType();
        String extension = EXTENSIONS.get(contentType);
        if (file.isEmpty() || extension == null || file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Image must be JPEG, PNG or WebP and at most 5 MB");
        }
        if (supabaseUrl.isBlank() || serviceRoleKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image storage is not configured");
        }

        String objectPath = folder + "/" + id + "/cover." + extension;
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(supabaseUrl + "/storage/v1/object/" + BUCKET + "/" + objectPath))
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("apikey", serviceRoleKey)
                    .header("Content-Type", contentType)
                    .header("x-upsert", "true")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Image storage upload failed");
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Image storage upload failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image upload interrupted", exception);
        }
        return supabaseUrl + "/storage/v1/object/public/" + BUCKET + "/" + objectPath;
    }

    private static String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceFirst("/+$", "");
    }
}