package com.idlegrid.backend.controller;

import com.idlegrid.backend.dto.ClientJobRequest;
import com.idlegrid.backend.dto.ClientLoginRequest;
import com.idlegrid.backend.dto.ClientLoginResponse;
import com.idlegrid.backend.dto.ClientNodeSummary;
import com.idlegrid.backend.dto.ClientRegisterRequest;
import com.idlegrid.backend.dto.DistributedTrainingStatus;
import com.idlegrid.backend.model.Job;
import com.idlegrid.backend.model.StoredFile;
import com.idlegrid.backend.service.ClientAuthService;
import com.idlegrid.backend.service.ClientJobService;
import com.idlegrid.backend.service.ClientTrainingHistoryService;
import com.idlegrid.backend.service.DistributedTrainingService;
import com.idlegrid.backend.service.StorageService;
import com.idlegrid.backend.store.NodeStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

/** Student-facing API. Admin, Agent, and DDP APIs remain separate from this surface. */
@RestController
@RequestMapping("/client")
public class ClientController {
    private final ClientAuthService auth;
    private final ClientJobService jobs;
    private final NodeStore nodes;
    private final StorageService storage;
    private final DistributedTrainingService training;
    private final ClientTrainingHistoryService trainingHistory;
    private final String configuredMasterUrl;

    public ClientController(ClientAuthService auth, ClientJobService jobs, NodeStore nodes,
                            StorageService storage, DistributedTrainingService training,
                            ClientTrainingHistoryService trainingHistory,
                            @Value("${rendezvous.master-url:}") String configuredMasterUrl) {
        this.auth = auth;
        this.jobs = jobs;
        this.nodes = nodes;
        this.storage = storage;
        this.training = training;
        this.trainingHistory = trainingHistory;
        this.configuredMasterUrl = configuredMasterUrl == null ? "" : configuredMasterUrl.trim();
    }

    @PostMapping("/auth/login")
    public ClientLoginResponse login(@RequestBody ClientLoginRequest request) {
        if (!auth.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No student accounts exist yet. Register from the client or preload one with IDLEGRID_CLIENT_USERS.");
        }
        return auth.login(request == null ? null : request.username(),
                request == null ? null : request.password());
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientLoginResponse register(@RequestBody ClientRegisterRequest request) {
        return auth.register(request == null ? null : request.username(),
                request == null ? null : request.password());
    }

    @PostMapping("/auth/logout")
    public Map<String, Boolean> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        auth.logout(token);
        return Map.of("loggedOut", true);
    }

    @GetMapping("/auth/me")
    public ClientLoginResponse me(@RequestHeader(value = "Authorization", required = false) String token) {
        String username = auth.usernameFor(token);
        return new ClientLoginResponse(null, username);
    }

    @GetMapping("/nodes")
    public Collection<ClientNodeSummary> nodes(
            @RequestHeader(value = "Authorization", required = false) String token) {
        auth.usernameFor(token);
        return nodes.all().stream().map(ClientNodeSummary::from).toList();
    }

    @GetMapping("/jobs")
    public Collection<Job> jobs(@RequestHeader(value = "Authorization", required = false) String token) {
        return jobs.list(auth.usernameFor(token));
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public Job submit(@RequestHeader(value = "Authorization", required = false) String token,
                      @RequestBody ClientJobRequest request) {
        return jobs.submit(auth.usernameFor(token), request);
    }

    @GetMapping("/jobs/{id}")
    public Job job(@RequestHeader(value = "Authorization", required = false) String token,
                   @PathVariable String id) {
        return jobs.get(auth.usernameFor(token), id);
    }

    @PostMapping("/jobs/{id}/cancel")
    public Job cancel(@RequestHeader(value = "Authorization", required = false) String token,
                      @PathVariable String id) {
        return jobs.cancel(auth.usernameFor(token), id);
    }

    @GetMapping("/storage/summary")
    public Map<String, Object> storageSummary(
            @RequestHeader(value = "Authorization", required = false) String token) {
        return storage.summaryForOwner(auth.usernameFor(token));
    }

    @GetMapping("/storage/files")
    public Collection<StoredFile> storedFiles(
            @RequestHeader(value = "Authorization", required = false) String token) {
        return storage.allForOwner(auth.usernameFor(token));
    }

    @PostMapping(value = "/storage/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam("file") MultipartFile file) {
        String username = auth.usernameFor(token);
        try {
            return ResponseEntity.ok(storage.upload(file, username));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Could not store uploaded file");
        }
    }

    @GetMapping("/storage/files/{key}/content")
    public ResponseEntity<Resource> downloadOwnedFile(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String key) {
        String username = auth.usernameFor(token);
        StoredFile file = storage.get(key);
        Path path = storage.contentPath(key);
        if (file == null || path == null || !username.equals(file.getOwnerUsername())) {
            return ResponseEntity.notFound().build();
        }
        MediaType contentType;
        try { contentType = MediaType.parseMediaType(file.getContentType()); }
        catch (IllegalArgumentException ignored) { contentType = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(path));
    }

    @PostMapping(value = "/training/runs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DistributedTrainingStatus submitTraining(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam("bundle") MultipartFile bundle,
            @RequestParam("command") String command,
            @RequestParam(defaultValue = "3") int workers,
            @RequestParam(defaultValue = "1") double cpuReq,
            @RequestParam(defaultValue = "2048") int ramReqMb,
            @RequestParam(defaultValue = "ghcr.io/avadhutmali/idlegrid-pytorch-cpu@sha256:74e60bc071f7349b0f7d9656f2105d7238152133e52dc71377b08eaa51883221") String image,
            @RequestParam(defaultValue = "model.pt") String outputFile,
            HttpServletRequest request) {
        String username = auth.usernameFor(token);
        DistributedTrainingStatus created = training.submit(bundle, command, workers, cpuReq,
                ramReqMb, image, outputFile, resolveMasterUrl(request));
        trainingHistory.record(username, created);
        return created;
    }

    @GetMapping("/training/runs")
    public Collection<DistributedTrainingStatus> trainingRuns(
            @RequestHeader(value = "Authorization", required = false) String token) {
        return trainingHistory.list(auth.usernameFor(token));
    }

    @GetMapping("/training/runs/{runId}")
    public DistributedTrainingStatus trainingRun(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String runId) {
        return trainingHistory.get(auth.usernameFor(token), runId);
    }

    @GetMapping("/training/runs/{runId}/artifact")
    public ResponseEntity<Resource> downloadTrainingArtifact(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String runId) {
        DistributedTrainingStatus status = trainingHistory.get(auth.usernameFor(token), runId);
        Path path = training.artifactPathFromHistory(runId, status.outputFile());
        if (!"COMPLETED".equals(status.status()) || path == null) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(path.getFileName().toString(), StandardCharsets.UTF_8).build().toString())
                    .body(new FileSystemResource(path));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String resolveMasterUrl(HttpServletRequest request) {
        if (!configuredMasterUrl.isBlank()) return trimSlash(configuredMasterUrl);
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getLocalPort() > 0 ? request.getLocalPort() : request.getServerPort();
        try {
            InetAddress requestAddress = InetAddress.getByName(host);
            if (requestAddress.isLoopbackAddress() || requestAddress.isAnyLocalAddress()) {
                try (DatagramSocket routeProbe = new DatagramSocket()) {
                    routeProbe.connect(InetAddress.getByName("api.jsonbin.io"), 443);
                    host = routeProbe.getLocalAddress().getHostAddress();
                    scheme = "http";
                }
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not determine a worker-reachable Master address for DDP workers. Configure rendezvous.master-url.");
        }
        String portPart = (("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443)) ? "" : ":" + port;
        return scheme + "://" + host + portPart;
    }

    private String trimSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
