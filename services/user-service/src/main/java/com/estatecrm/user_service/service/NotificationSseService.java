package com.estatecrm.user_service.service;

import com.estatecrm.user_service.dto.notification.NotificationResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NotificationSseService {

    private static final Logger log = LoggerFactory.getLogger(NotificationSseService.class);
    private static final Long EMITTER_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    private final Map<UUID, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);

        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            removeEmitter(userId, emitter);
        });
        emitter.onError(e -> {
            emitter.complete();
            removeEmitter(userId, emitter);
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data(Map.of(
                            "status", "CONNECTED",
                            "userId", userId.toString(),
                            "timestamp", System.currentTimeMillis())));
            log.debug("User {} subscribed to notification SSE stream", userId);
        } catch (IOException e) {
            removeEmitter(userId, emitter);
        }

        return emitter;
    }

    public void sendToUser(UUID userId, NotificationResponse notification) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(notification.id().toString())
                        .name("notification")
                        .data(notification));
            } catch (Exception e) {
                removeEmitter(userId, emitter);
            }
        }
        log.info("Sent realtime notification {} to user {}", notification.id(), userId);
    }

    public void broadcast(NotificationResponse notification) {
        userEmitters.keySet().forEach(userId -> sendToUser(userId, notification));
    }

    @Scheduled(fixedDelay = 25000)
    public void sendHeartbeat() {
        if (userEmitters.isEmpty()) {
            return;
        }

        userEmitters.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    removeEmitter(userId, emitter);
                }
            }
        });
    }

    private void removeEmitter(UUID userId, SseEmitter emitter) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
    }
}
