package com.idlegrid.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * IdleGrid Master node.
 *
 * Phase 1 scope: in-memory node/job registry, no database, no auth.
 * See /docs/PROJECT_CONTEXT.md at the repo root for full architecture context.
 */
@SpringBootApplication
@EnableScheduling
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
