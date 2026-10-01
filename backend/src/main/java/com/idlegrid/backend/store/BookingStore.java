package com.idlegrid.backend.store;

import com.idlegrid.backend.dto.BookingRequest;
import com.idlegrid.backend.model.Booking;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BookingStore {
    private final Map<String, Booking> bookings = new ConcurrentHashMap<>();

    public synchronized Booking create(BookingRequest request) {
        validate(request);
        boolean overlaps = bookings.values().stream()
                .filter(existing -> existing.getNodeId().equals(request.nodeId()))
                .anyMatch(existing -> request.startAt().isBefore(existing.getEndAt())
                        && request.endAt().isAfter(existing.getStartAt()));
        if (overlaps) {
            throw new IllegalArgumentException("This node is already booked for part of that time window");
        }

        Booking booking = new Booking(UUID.randomUUID().toString(), request.nodeId(),
                request.requester().trim(), request.startAt(), request.endAt(),
                request.cpuReq(), request.ramReqMb());
        bookings.put(booking.getId(), booking);
        return booking;
    }

    public Collection<Booking> all() {
        return bookings.values().stream()
                .sorted((left, right) -> left.getStartAt().compareTo(right.getStartAt()))
                .toList();
    }

    public Booking get(String id) {
        return bookings.get(id);
    }

    public boolean delete(String id) {
        return bookings.remove(id) != null;
    }

    private void validate(BookingRequest request) {
        if (request.nodeId() == null || request.nodeId().isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        if (request.requester() == null || request.requester().isBlank()) {
            throw new IllegalArgumentException("requester is required");
        }
        if (request.startAt() == null || request.endAt() == null
                || !request.startAt().isBefore(request.endAt())) {
            throw new IllegalArgumentException("startAt must be before endAt");
        }
        if (request.startAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("startAt cannot be in the past");
        }
        if (request.cpuReq() <= 0 || request.ramReqMb() <= 0) {
            throw new IllegalArgumentException("CPU and RAM requests must be positive");
        }
    }
}