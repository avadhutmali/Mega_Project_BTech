package com.idlegrid.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlegrid.backend.dto.ClientLoginResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** File-backed student accounts with short-lived in-memory sessions for the prototype. */
@Service
public class ClientAuthService {
    private static final long SESSION_HOURS = 12;
    private static final int HASH_ITERATIONS = 120_000;
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,32}$");

    private final Map<String, StoredCredential> credentials = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final ObjectMapper mapper;
    private final Path usersFile;
    private final Map<String, String> configuredUsers = new HashMap<>();
    private final int maxUsers;

    public ClientAuthService(
            ObjectMapper mapper,
            @Value("${idlegrid.client.users:}") String configuredUsers,
            @Value("${idlegrid.storage.directory:./storage}") String storageDirectory,
            @Value("${idlegrid.client.max-users:50}") int maxUsers) {
        this.mapper = mapper;
        this.usersFile = Paths.get(storageDirectory).toAbsolutePath().normalize().resolve("users.json");
        this.maxUsers = maxUsers;
        if (configuredUsers != null) {
            for (String rawEntry : configuredUsers.split(",")) {
                String entry = rawEntry.trim();
                int separator = entry.indexOf(':');
                if (separator <= 0 || separator == entry.length() - 1) continue;
                String username = entry.substring(0, separator).trim();
                String password = entry.substring(separator + 1);
                if (USERNAME.matcher(username).matches() && !password.isBlank()) {
                    this.configuredUsers.put(username, password);
                }
            }
        }
    }

    @PostConstruct
    void loadUsers() {
        try {
            Files.createDirectories(usersFile.getParent());
            if (Files.isRegularFile(usersFile)) {
                Map<String, StoredCredential> restored = mapper.readValue(usersFile.toFile(),
                        new TypeReference<>() {});
                if (restored != null) credentials.putAll(restored);
            }
            boolean changed = false;
            for (var entry : configuredUsers.entrySet()) {
                if (!credentials.containsKey(entry.getKey())) {
                    credentials.put(entry.getKey(), hashPassword(entry.getValue()));
                    changed = true;
                }
            }
            if (changed) saveUsers();
        } catch (Exception e) {
            throw new IllegalStateException("Could not load student accounts from " + usersFile, e);
        }
    }

    public ClientLoginResponse login(String username, String password) {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        StoredCredential expected = username == null ? null : credentials.get(username);
        if (expected == null || password == null || !passwordMatches(password, expected)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        sessions.put(token, new Session(username, now.plusSeconds(SESSION_HOURS * 3600)));
        return new ClientLoginResponse(token, username);
    }

    public synchronized ClientLoginResponse register(String username, String password) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Username must be 3–32 characters: letters, numbers, dot, underscore, or hyphen.");
        }
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be between 8 and 128 characters.");
        }
        if (credentials.containsKey(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already registered.");
        }
        if (credentials.size() >= maxUsers) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The Master has reached its configured student account limit.");
        }
        credentials.put(username, hashPassword(password));
        try {
            saveUsers();
        } catch (IOException e) {
            credentials.remove(username);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not save the new account on the Master.");
        }
        return login(username, password);
    }

    public String usernameFor(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in to the student client");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        Session session = sessions.get(token);
        if (session == null || session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your session expired. Please sign in again.");
        }
        return session.username();
    }

    public void logout(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            sessions.remove(authorization.substring("Bearer ".length()).trim());
        }
    }

    public boolean isConfigured() { return !credentials.isEmpty() || Files.exists(usersFile); }

    private StoredCredential hashPassword(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt);
        return new StoredCredential(Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(hash));
    }

    private boolean passwordMatches(String password, StoredCredential credential) {
        try {
            byte[] salt = Base64.getDecoder().decode(credential.salt());
            byte[] expected = Base64.getDecoder().decode(credential.passwordHash());
            return MessageDigest.isEqual(expected, derive(password, salt));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash student password", e);
        } finally {
            spec.clearPassword();
        }
    }

    private synchronized void saveUsers() throws IOException {
        Path temporary = usersFile.resolveSibling(usersFile.getFileName() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), credentials);
        try {
            Files.move(temporary, usersFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unsupportedAtomicMove) {
            Files.move(temporary, usersFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record StoredCredential(String salt, String passwordHash) {}

    private record Session(String username, Instant expiresAt) {}
}
