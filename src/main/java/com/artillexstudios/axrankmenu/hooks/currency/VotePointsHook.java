package com.artillexstudios.axrankmenu.hooks.currency;

import com.artillexstudios.axrankmenu.AxRankMenu;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import static com.artillexstudios.axrankmenu.AxRankMenu.CONFIG;

public final class VotePointsHook implements CurrencyHook {
    private static final Pattern BALANCE = Pattern.compile("\"balance\"\\s*:\\s*(\\d+)");
    private final ConcurrentMap<UUID, Long> balanceCache = new ConcurrentHashMap<>();
    private HttpClient client;
    private URI baseUri;
    private String apiKey;
    private Duration requestTimeout;

    @Override
    public void setup() {
        String configuredUrl = CONFIG.getString("hooks.VotePoints.base-url", "");
        String configuredKey = CONFIG.getString("hooks.VotePoints.api-key", "");
        int connectTimeout = CONFIG.getInt("hooks.VotePoints.connect-timeout-ms", 2000);
        int requestTimeoutMillis = CONFIG.getInt("hooks.VotePoints.request-timeout-ms", 4000);
        if (configuredUrl == null || configuredUrl.isBlank() || configuredKey == null
                || configuredKey.length() < 32 || connectTimeout < 250 || requestTimeoutMillis < 250) {
            throw new IllegalStateException("VotePoints requires a URL, a 32-character API key, and valid timeouts.");
        }
        URI uri = URI.create(configuredUrl.endsWith("/") ? configuredUrl : configuredUrl + "/");
        String scheme = uri.getScheme();
        boolean localHttp = "http".equalsIgnoreCase(scheme) && uri.getHost() != null
                && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost())
                || "::1".equals(uri.getHost()));
        if (!"https".equalsIgnoreCase(scheme) && !localHttp) {
            throw new IllegalStateException("VotePoints API must use HTTPS unless it is bound to loopback.");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || uri.getHost() == null) {
            throw new IllegalStateException("VotePoints API URL must not contain credentials, query, or fragment.");
        }
        this.baseUri = uri;
        this.apiKey = configuredKey;
        this.requestTimeout = Duration.ofMillis(requestTimeoutMillis);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String getName() {
        return "VotePoints";
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public double getBalance(@NotNull Player player) {
        return balanceCache.getOrDefault(player.getUniqueId(), 0L);
    }

    @Override
    public void giveBalance(@NotNull Player player, double amount) {
        Bukkit.getLogger().warning("VotePoints cannot be manually credited through AxRankMenu; "
                + "points are awarded by valid Votifae votes.");
    }

    @Override
    public void takeBalance(@NotNull Player player, double amount) {
        long points = toPoints(amount);
        if (points <= 0) {
            throw new IllegalArgumentException("Vote point amount must be positive.");
        }
        UUID playerUuid = player.getUniqueId();
        debit(playerUuid, points, UUID.randomUUID().toString())
                .thenAccept(debited -> {
                    if (!debited) {
                        Bukkit.getLogger().warning("VotePoints debit failed: insufficient balance for "
                                + playerUuid);
                    }
                })
                .exceptionally(error -> {
                    logFailure("debit", error);
                    return null;
                });
    }

    public CompletableFuture<Long> refreshBalance(UUID playerUuid) {
        HttpRequest request = HttpRequest.newBuilder(endpoint(playerUuid))
                .timeout(requestTimeout)
                .header("X-Api-Key", apiKey)
                .header("Accept", "application/json")
                .GET()
                .build();
        return sendWithRetry(request, 2)
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new CompletionException(new IOException(
                                "VotePoints API balance request returned HTTP " + response.statusCode()));
                    }
                    long balance = parseBalance(response.body());
                    balanceCache.put(playerUuid, balance);
                    return balance;
                });
    }

    public CompletableFuture<Boolean> debit(UUID playerUuid, long amount, String idempotencyKey) {
        if (amount <= 0 || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Vote point debit requires a positive amount and idempotency key.");
        }
        String body = "{\"amount\":" + amount + ",\"idempotencyKey\":\"" + idempotencyKey
                + "\",\"reason\":\"axrankmenu-rank-purchase\"}";
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                        "v1/vote-points/" + playerUuid + "/debit"))
                .timeout(requestTimeout)
                .header("X-Api-Key", apiKey)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return sendWithRetry(request, 2)
                .thenApply(response -> {
                    if (response.statusCode() == 409
                            && response.body().contains("\"insufficient_balance\"")) {
                        return false;
                    }
                    if (response.statusCode() != 200) {
                        throw new CompletionException(new IOException(
                                "VotePoints API debit request returned HTTP " + response.statusCode()));
                    }
                    balanceCache.put(playerUuid, parseBalance(response.body()));
                    return true;
                });
    }

    private CompletableFuture<HttpResponse<String>> sendWithRetry(HttpRequest request, int retriesRemaining) {
        CompletableFuture<HttpResponse<String>> result = new CompletableFuture<>();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
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

    private URI endpoint(UUID playerUuid) {
        return baseUri.resolve("v1/vote-points/" + playerUuid);
    }

    private long parseBalance(String responseBody) {
        Matcher matcher = BALANCE.matcher(responseBody);
        if (!matcher.find()) {
            throw new CompletionException(new IllegalStateException(
                    "VotePoints API returned an invalid balance response."));
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new CompletionException(new IllegalStateException(
                    "VotePoints API returned an out-of-range balance.", exception));
        }
    }

    private long toPoints(double amount) {
        if (!Double.isFinite(amount) || amount <= 0 || amount > Long.MAX_VALUE
                || amount != Math.rint(amount)) {
            throw new IllegalArgumentException("Vote point amounts must be positive whole numbers.");
        }
        return (long) amount;
    }

    private void logFailure(String operation, Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause() : error;
        AxRankMenu.getInstance().getLogger().log(Level.SEVERE,
                "VotePoints " + operation + " failed; no successful result was reported.", cause);
    }
}
