package com.example.outbox.outbox;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /** The relay's query: oldest-first batch of events still waiting to be published. */
    List<OutboxEvent> findByProcessedFalseOrderByIdAsc(Limit limit);
}