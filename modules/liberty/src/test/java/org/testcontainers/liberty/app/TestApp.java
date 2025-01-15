package org.testcontainers.liberty.app;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("/app")
public class TestApp extends Application {}
