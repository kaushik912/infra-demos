package com.example.outbox.registration;

import com.example.outbox.outbox.OutboxEvent;
import com.example.outbox.outbox.OutboxRepository;
import com.example.outbox.user.AppUser;
import com.example.outbox.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public RegistrationService(UserRepository userRepository,
                               OutboxRepository outboxRepository,
                               ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * The crux of the pattern: the user row AND the outbox row are written inside
     * ONE transaction. They commit together or not at all. We do NOT touch Kafka
     * here — publishing is deferred to the relay so a broker hiccup can never roll
     * back (or falsely commit) the registration.
     */
    @Transactional
    public AppUser register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalStateException("username already taken: " + request.username());
        }

        // 1. business change
        AppUser user = userRepository.save(new AppUser(request.username(), request.email()));

        // 2. record the event to publish, in the SAME transaction
        String payload = toJson(user);
        outboxRepository.save(new OutboxEvent(
                "user",
                String.valueOf(user.getId()),
                "UserRegistered",
                payload));

        return user;
    }

    private String toJson(AppUser user) {
        // Jackson 3 (tools.jackson.*) throws unchecked JacksonException, so no try/catch needed.
        ObjectNode node = objectMapper.createObjectNode();
        node.put("eventType", "UserRegistered");
        node.put("userId", user.getId());
        node.put("username", user.getUsername());
        node.put("email", user.getEmail());
        node.put("registeredAt", user.getCreatedAt().toString());
        return objectMapper.writeValueAsString(node);
    }
}