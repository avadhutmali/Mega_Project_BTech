package com.idlegrid.backend.controller;

import com.idlegrid.backend.dto.BookingRequest;
import com.idlegrid.backend.model.Booking;
import com.idlegrid.backend.model.Node;
import com.idlegrid.backend.store.BookingStore;
import com.idlegrid.backend.store.NodeStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/bookings")
public class BookingController {
    private final BookingStore bookingStore;
    private final NodeStore nodeStore;

    public BookingController(BookingStore bookingStore, NodeStore nodeStore) {
        this.bookingStore = bookingStore;
        this.nodeStore = nodeStore;
    }

    @GetMapping
    public Collection<Booking> all() {
        return bookingStore.all();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody BookingRequest request) {
        Node node = nodeStore.get(request.nodeId());
        if (node == null || node.getStatus().name().equals("OFFLINE")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Node is not online");
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(bookingStore.create(request));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(error.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return bookingStore.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}