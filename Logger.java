package com.loggersimple.logging;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * HTTP Logger vers https://logger-simple.com/
 */
public class Logger {
    private final String appId;
    private final String apiKey;
    private final String apiUrl = "https://api.logger-simple.com/java/";
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final long heartbeatIntervalMs;

    public Logger(String appId, String apiKey, long heartbeatIntervalMs) {
        this.appId = Objects.requireNonNull(appId, "appId est requis");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey est requis");
        this.heartbeatIntervalMs = heartbeatIntervalMs > 0 ? heartbeatIntervalMs : 5000L;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        startOnlineStatusCheck();
        setupCrashLogging();
    }

    public Logger(String appId, String apiKey) {
        this(appId, apiKey, 5000L);
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String buildUrl(String action, String request, String level, String message) {
        StringBuilder sb = new StringBuilder(apiUrl)
            .append("?action=").append(encode(action))
            .append("&request=").append(encode(request))
            .append("&app_id=").append(encode(appId))
            .append("&api_key=").append(encode(apiKey));
        if (level != null) sb.append("&logLevel=").append(encode(level));
        if (message != null) sb.append("&message=").append(encode(message));
        return sb.toString();
    }

    private HttpResponse<String> sendGet(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private String sendLog(String level, String message) throws IOException, InterruptedException {
        String url = buildUrl("logger", "new_log", level, message);
        HttpResponse<String> resp = sendGet(url);
        if (resp.statusCode() == 200 && resp.body().contains("\"success\":true")) {
            return resp.body();
        }
        throw new IOException("Erreur API log: " + resp.body());
    }

    public String logSuccess(String msg) throws IOException, InterruptedException {
        return sendLog("success", msg);
    }
    public String logInfo(String msg)    throws IOException, InterruptedException {
        return sendLog("info", msg);
    }
    public String logError(String msg)   throws IOException, InterruptedException {
        return sendLog("error", msg);
    }
    public String logCritical(String msg)throws IOException, InterruptedException {
        return sendLog("critical", msg);
    }

    private void startOnlineStatusCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            try { sendHeartbeat(); }
            catch (Exception ignored) {}
        }, 0, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }

    public String sendHeartbeat() throws IOException, InterruptedException {
        String url = buildUrl("logger", "heartbeat", null, null);
        HttpResponse<String> resp = sendGet(url);
        if (resp.statusCode() == 200 && resp.body().contains("\"success\":true")) {
            return resp.body();
        }
        throw new IOException("Erreur API heartbeat: " + resp.body());
    }

    public void stopOnlineStatusCheck() {
        scheduler.shutdownNow();
    }

    private void setupCrashLogging() {
        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> {
            try {
                logCritical("Uncaught Exception - " + ex);
            } catch (Exception e) {
                System.err.println("Échec log critique: " + e.getMessage());
            }
            System.exit(1);
        });
    }
}
