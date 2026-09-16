package com.estatecrm.user_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;


@Component
public class LoginAttemptService {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public void checkBlocked(String key, int maxAttempts) {
        Deque<Instant> timestamps = attempts.get(key);
        if (timestamps == null) {
            return;
        }
        Instant cutoff = Instant.now().minus(WINDOW);
        synchronized (timestamps) {
            timestamps.removeIf(instant -> instant.isBefore(cutoff));
            if (timestamps.size() >= maxAttempts) {
                throw new ResponseStatusException(
                        TOO_MANY_REQUESTS, "Too many failed login attempts. Try again later.");
            }
        }
    }

    public void recordFailure(String key) {
        Deque<Instant> timestamps = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            timestamps.addLast(Instant.now());
        }
    }

    public void reset(String key) {
        attempts.remove(key);
    }
}
