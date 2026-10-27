package com.idlegrid.backend.controller;

import com.idlegrid.backend.model.StoredFile;
import com.idlegrid.backend.service.StorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;

@RestController
@RequestMapping("/storage")
public class StorageController {
    private final StorageService storage;
    public StorageController(StorageService storage) { this.storage = storage; }

    @GetMapping("/summary")
    public Object summary() { return storage.summary(); }

    @GetMapping("/files")
    public Collection<StoredFile> files() { return storage.all(); }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(storage.upload(file));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Could not store uploaded file");
        }
    }

    /** Possession of the random key grants access to this file. */
    @GetMapping("/files/{key}/content")
    public ResponseEntity<Resource> download(@PathVariable String key) {
        StoredFile file = storage.get(key);
        Path path = storage.contentPath(key);
        if (file == null || path == null) return ResponseEntity.notFound().build();
        MediaType contentType;
        try { contentType = MediaType.parseMediaType(file.getContentType()); }
        catch (IllegalArgumentException ignored) { contentType = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(path));
    }
}
