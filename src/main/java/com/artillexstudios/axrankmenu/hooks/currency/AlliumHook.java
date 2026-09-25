package com.artillexstudios.axrankmenu.hooks.currency;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.artillexstudios.axrankmenu.AxRankMenu.CONFIG;

public class AlliumHook implements CurrencyHook {
    private static final Pattern BALANCE_PATTERN = Pattern.compile("\"balance\"\\s*:\\s*(-?\\d+)");
    private static final Pattern ERROR_PATTERN = Pattern.compile("\"error\"\\s*:\\s*\"([^\"]+)\"");

    private HttpClient httpClient;
    private URI baseUrl;
    private String apiKey;
    private Duration requestTimeout;

    @Override
    public void setup() {
        final String baseUrlValue = CONFIG.getString("hooks.Allium.base-url", "http://127.0.0.1:8765");
        final String apiKeyValue = CONFIG.getString("hooks.Allium.api-key", "");
        final int connectTimeoutMillis = CONFIG.getInt("hooks.Allium.connect-timeout-ms", 2000);
        final int requestTimeoutMillis = CONFIG.getInt("hooks.Allium.request-timeout-ms", 4000);

        if (baseUrlValue == null || baseUrlValue.isBlank()) {
            throw new IllegalStateException("Allium base-url is not configured");
        }
        if (apiKeyValue == null || apiKeyValue.length() < 32) {
            throw new IllegalStateException("Allium api-key must contain at least 32 characters");
        }

        URI configuredUri = URI.create(baseUrlValue.endsWith("/") ? baseUrlValue : baseUrlValue + "/");
        String scheme = configuredUri.getScheme();
        boolean localHttp = "http".equalsIgnoreCase(scheme) && configuredUri.getHost() != null
                && ("localhost".equalsIgnoreCase(configuredUri.getHost())
                || "127.0.0.1".equals(configuredUri.getHost())
                || "::1".equals(configuredUri.getHost()));
        if (!"https".equalsIgnoreCase(scheme) && !localHttp
                || configuredUri.getHost() == null || configuredUri.getUserInfo() != null
                || configuredUri.getQuery() != null || configuredUri.getFragment() != null) {
            throw new IllegalStateException("Allium API must use HTTPS unless it is on loopback, without URL credentials.");
        }
        this.baseUrl = configuredUri;
        this.apiKey = apiKeyValue;
        if (connectTimeoutMillis < 250 || requestTimeoutMillis < 250) {
            throw new IllegalStateException("Allium request timeouts must be at least 250ms");
        }
        this.requestTimeout = Duration.ofMillis(requestTimeoutMillis);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String getName() {
        return "Allium";
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public double getBalance(@NotNull Player p) {
        try {
            return parseBalance(callApi("GET", p.getUniqueId().toString(), null));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0D;
        }
    }

    @Override
    public void giveBalance(@NotNull Player p, double amount) {
        try {
            callApi("POST", p.getUniqueId().toString() + "/credit", String.valueOf(Math.round(amount)));
        } catch (IOException | InterruptedException ignored) {
        }
    }

    @Override
    public void takeBalance(@NotNull Player p, double amount) {
        try {
            callApi("POST", p.getUniqueId().toString() + "/debit", String.valueOf(Math.round(amount)));
        } catch (IOException | InterruptedException ignored) {
        }
    }

    public CompletableFuture<Boolean> debit(UUID playerUuid, long amount, String idempotencyKey) {
        String payload = "{\"amount\":" + amount + ",\"idempotencyKey\":\"" + idempotencyKey
                + "\",\"reason\":\"axrankmenu-shop\"}";
        HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve(
                        "v1/currency/" + playerUuid + "/debit"))
                .timeout(requestTimeout)
                .header("X-Api-Key", apiKey)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        return sendWithRetry(request, 2)
                .thenApply(response -> {
                    if (response.statusCode() == 409) {
                        return false;
                    }
                    if (response.statusCode() != 200) {
                        throw new CompletionException(new IOException(
                                "Allium API debit returned HTTP " + response.statusCode()));
                    }
                    return true;
                });
    }

    private CompletableFuture<HttpResponse<String>> sendWithRetry(HttpRequest request, int retriesRemaining) {
        CompletableFuture<HttpResponse<String>> result = new CompletableFuture<>();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            if ((error != null || response.statusCode() >= 500) && retriesRemaining > 0) {
                CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS).execute(() ->
                        sendWithRetry(request, retriesRemaining - 1).whenComplete((retryResponse, retryError) -> {
                            if (retryError != null) {
                                result.completeExceptionally(retryError);
                            } else {
                                result.complete(retryResponse);
                            }
                        }));
            } else if (error != null) {
                result.completeExceptionally(error);
            } else {
                result.complete(response);
            }
        });
        return result;
    }

    private long parseBalance(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return 0L;
        }

        final Matcher matcher = BALANCE_PATTERN.matcher(responseBody);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }

        final Matcher errorMatcher = ERROR_PATTERN.matcher(responseBody);
        if (errorMatcher.find()) {
            throw new IllegalStateException("Allium API error: " + errorMatcher.group(1));
        }

        return 0L;
    }

    private String callApi(String method, String path, String amount) throws IOException, InterruptedException {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(baseUrl.resolve("v1/currency/" + path))
                .header("X-Api-Key", apiKey)
                .header("Accept", "application/json");

        if ("GET".equalsIgnoreCase(method)) {
            builder.GET();
        } else {
            if (amount == null) {
                throw new IllegalArgumentException("Amount required for Allium transaction");
            }
            final String payload = "{\"amount\":" + amount + ",\"idempotencyKey\":\"axrankmenu-" + System.currentTimeMillis() + "-" + path.replace("/", "-") + "\",\"reason\":\"axrankmenu-shop\"}";
            builder.POST(HttpRequest.BodyPublishers.ofString(payload))
                    .header("Content-Type", "application/json");
        }

        final HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Allium API returned HTTP " + response.statusCode() + " body=" + response.body());
        }
        return response.body();
    }
}
