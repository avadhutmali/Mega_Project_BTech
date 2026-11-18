package com.idlegrid.backend.controller;

import com.idlegrid.backend.dto.FederatedTrainingStatus;
import com.idlegrid.backend.service.FederatedTrainingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** HTTP API for the coordinator-mediated FedAvg demo. */
@RestController
@RequestMapping("/federated-training")
public class FederatedTrainingController {
    private final FederatedTrainingService training;
    private final ObjectMapper mapper;

    public FederatedTrainingController(FederatedTrainingService training, ObjectMapper mapper) {
        this.training = training;
        this.mapper = mapper;
    }

    @PostMapping(value = "/runs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FederatedTrainingStatus submit(
            @RequestParam("bundle") MultipartFile bundle,
            @RequestParam(defaultValue = "3") int workers,
            @RequestParam(defaultValue = "1") double cpuReq,
            @RequestParam(defaultValue = "2048") int ramReqMb,
            @RequestParam(defaultValue = "2") int localEpochs,
            @RequestParam(defaultValue = "ghcr.io/avadhutmali/idlegrid-pytorch-cpu@sha256:74e60bc071f7349b0f7d9656f2105d7238152133e52dc71377b08eaa51883221") String image,
            HttpServletRequest request) {
        return training.submit(bundle, workers, cpuReq, ramReqMb, localEpochs, image, resolveMasterUrl(request));
    }

    @GetMapping("/runs")
    public java.util.List<FederatedTrainingStatus> all() { return training.all(); }

    @GetMapping("/runs/{runId}")
    public FederatedTrainingStatus status(@PathVariable String runId) { return training.status(runId); }

    @GetMapping("/runs/{runId}/bundle")
    public ResponseEntity<Resource> bundle(@PathVariable String runId) {
        Path path = training.bundlePath(runId);
        if (!Files.isRegularFile(path)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("idlegrid-fedavg-bundle.zip").build().toString())
                .body(new FileSystemResource(path));
    }

    @PostMapping(value = "/runs/{runId}/updates/{rank}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadUpdate(@PathVariable String runId, @PathVariable int rank,
                                          @RequestParam("checkpoint") MultipartFile checkpoint) {
        try {
            Path saved = training.saveUpdate(runId, rank, checkpoint);
            return ResponseEntity.ok(java.util.Map.of("rank", rank, "sizeBytes", Files.size(saved)));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Could not save worker model update.");
        }
    }

    @GetMapping("/runs/{runId}/updates-ready")
    public ResponseEntity<?> updatesReady(@PathVariable String runId) {
        if (training.updatesReady(runId)) return ResponseEntity.ok(java.util.Map.of("ready", true));
        return ResponseEntity.status(425).body(java.util.Map.of("ready", false, "received", training.status(runId).workers().stream()
                .filter(com.idlegrid.backend.dto.FederatedWorkerStatus::updateUploaded).count()));
    }

    @GetMapping("/runs/{runId}/updates/{rank}")
    public ResponseEntity<Resource> update(@PathVariable String runId, @PathVariable int rank) {
        Path path = training.updatePath(runId, rank);
        if (path == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(new FileSystemResource(path));
    }

    @PostMapping(value = "/runs/{runId}/artifact", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> artifact(@PathVariable String runId, @RequestParam("file") MultipartFile file) {
        try {
            Path saved = training.saveArtifact(runId, file);
            return ResponseEntity.ok(java.util.Map.of("fileName", saved.getFileName().toString(), "sizeBytes", Files.size(saved)));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Could not store the aggregated model.");
        }
    }

    @PostMapping(value = "/runs/{runId}/comparison/{kind}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> comparison(@PathVariable String runId, @PathVariable String kind,
                                        @RequestBody Map<String, Object> metrics) {
        training.saveComparison(runId, kind, metrics);
        return ResponseEntity.ok(Map.of("stored", kind));
    }

    @GetMapping("/runs/{runId}/artifact")
    public ResponseEntity<Resource> artifact(@PathVariable String runId) {
        Path path = training.artifactPath(runId);
        if (path == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("model.pt").build().toString())
                .body(new FileSystemResource(path));
    }

    @GetMapping(value = "/runs/{runId}/report", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> report(@PathVariable String runId) {
        try {
            byte[] report = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(training.status(runId));
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("training-report.json").build().toString())
                    .body(report);
        } catch (JsonProcessingException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String resolveMasterUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getLocalPort() > 0 ? request.getLocalPort() : request.getServerPort();
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
                try (DatagramSocket probe = new DatagramSocket()) {
                    probe.connect(InetAddress.getByName("api.jsonbin.io"), 443);
                    host = probe.getLocalAddress().getHostAddress();
                    scheme = "http";
                }
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not determine the worker-reachable Master URL; configure rendezvous.master-url.");
        }
        return scheme + "://" + host + (("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443) ? "" : ":" + port);
    }

}
