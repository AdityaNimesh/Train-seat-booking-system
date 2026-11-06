package com.example.trainbooking;

import com.example.trainbooking.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/** One shared throw-away PostgreSQL container for all integration tests (requires Docker). */
abstract class PostgresTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected AdminService adminService;

    @BeforeEach
    void resetCoach() {
        adminService.reset();
    }
}
