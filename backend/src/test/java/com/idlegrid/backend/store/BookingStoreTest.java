package com.idlegrid.backend.store;

import com.idlegrid.backend.dto.BookingRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingStoreTest {
    private final BookingStore store = new BookingStore();

    @Test
    void rejectsOverlappingBookingsForTheSameNode() {
        Instant start = Instant.now().plusSeconds(3600);
        store.create(request("node-a", start, start.plusSeconds(3600)));

        assertThrows(IllegalArgumentException.class,
                () -> store.create(request("node-a", start.plusSeconds(1800), start.plusSeconds(5400))));
    }

    @Test
    void permitsSeparateWindowsAndNodes() {
        Instant start = Instant.now().plusSeconds(3600);
        store.create(request("node-a", start, start.plusSeconds(3600)));

        assertDoesNotThrow(() -> store.create(request("node-a", start.plusSeconds(3600), start.plusSeconds(5400))));
        assertDoesNotThrow(() -> store.create(request("node-b", start, start.plusSeconds(3600))));
    }

    private BookingRequest request(String nodeId, Instant start, Instant end) {
        return new BookingRequest(nodeId, "student", start, end, 1, 512);
    }
}