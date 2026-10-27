package com.idlegrid.backend.controller;

import com.idlegrid.backend.dto.DistributedTrainingStatus;
import com.idlegrid.backend.service.DistributedTrainingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/distributed-training")
public class DistributedTrainingController {
    private final DistributedTrainingService training;
    private final String configuredMasterUrl;

    public DistributedTrainingController(DistributedTrainingService training,
                                         @Value("${rendezvous.master-url:}") String configuredMasterUrl) {
        this.training = training;
        this.configuredMasterUrl = configuredMasterUrl == null ? "" : configuredMasterUrl.trim();
    }

    @PostMapping(value = "/runs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DistributedTrainingStatus submit(
            @RequestParam("bundle") MultipartFile bundle,
            @RequestParam("command") String command,
            @RequestParam(defaultValue = "3") int workers,
            @RequestParam(defaultValue = "1") double cpuReq,
            @RequestParam(defaultValue = "2048") int ramReqMb,
            @RequestParam(defaultValue = "idlegrid-pytorch-cpu:latest") String image,
            @RequestParam(defaultValue = "model.pt") String outputFile,
            HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        if (!configuredMasterUrl.isBlank()) return training.submit(bundle, command, workers, cpuReq, ramReqMb,
                image, outputFile, trimSlash(configuredMasterUrl));
        try {
            InetAddress requestAddress = InetAddress.getByName(host);
            if (requestAddress.isLoopbackAddress() || requestAddress.isAnyLocalAddress()) {
                try (DatagramSocket routeProbe = new DatagramSocket()) {
                    routeProbe.connect(InetAddress.getByName("api.jsonbin.io"), 443);
                    host = routeProbe.getLocalAddress().getHostAddress();
                }
            }
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Could not determine a network-reachable Master address for DDP workers.");
        }
        String masterUrl = scheme + "://" + host
                + (("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443) ? "" : ":" + port);
        return training.submit(bundle, command, workers, cpuReq, ramReqMb, image, outputFile, masterUrl);
    }

    private String trimSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    @GetMapping("/runs")
    public List<DistributedTrainingStatus> all() { return training.all(); }

    @GetMapping("/runs/{runId}")
    public DistributedTrainingStatus status(@PathVariable String runId) { return training.status(runId); }

    /** Agent containers download the same input bundle; the user's DDP script shards samples by rank. */
    @GetMapping("/runs/{runId}/bundle")
    public ResponseEntity<Resource> bundle(@PathVariable String runId) {
        Path path = training.bundlePath(runId);
        if (path == null || !Files.isRegularFile(path)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("idlegrid-training-bundle.zip").build().toString())
                .body(new FileSystemResource(path));
    }

    /** Rank zero uploads the final model artifact after torchrun completes. */
    @PostMapping(value = "/runs/{runId}/artifact", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> artifact(@PathVariable String runId, @RequestParam("file") MultipartFile file) {
        try {
            Path saved = training.saveArtifact(runId, file);
            return ResponseEntity.ok(java.util.Map.of("fileName", saved.getFileName().toString(), "sizeBytes", Files.size(saved)));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Could not store model artifact");
        }
    }

    @GetMapping("/runs/{runId}/artifact")
    public ResponseEntity<Resource> downloadArtifact(@PathVariable String runId) {
        Path path = training.artifactPath(runId);
        if (path == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(path.getFileName().toString(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(path));
    }
}
