package com.artillexstudios.axrankmenu.hooks.currency;

import com.artillexstudios.axrankmenu.AxRankMenu;
import dev.noellx.allium.api.AlliumCurrencyService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class AlliumHook implements CurrencyHook {
    private static final long BALANCE_CACHE_TTL_MILLIS = 5_000L;
    private static final int MAX_BALANCE_CACHE_ENTRIES = 4096;
    private static final int MAX_PENDING_BALANCE_LOOKUPS = 128;
    private final Set<UUID> pendingLookups = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BalanceCacheEntry> balanceCache = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, BalanceCacheEntry> eldest) {
                    return size() > MAX_BALANCE_CACHE_ENTRIES;
                }
            });
    private AlliumCurrencyService service;

    @Override
    public void setup() {
        service = Bukkit.getServicesManager().load(AlliumCurrencyService.class);
        if (service == null) {
            throw new IllegalStateException("Allium Bukkit currency service is unavailable.");
        }
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
    public double getBalance(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        BalanceCacheEntry entry;
        boolean refresh = false;
        synchronized (balanceCache) {
            entry = balanceCache.get(uuid);
            if (entry == null) {
                entry = new BalanceCacheEntry(0L, 0L);
            }
            if (now - entry.lastAttemptAt() >= BALANCE_CACHE_TTL_MILLIS) {
                entry = new BalanceCacheEntry(entry.balance(), now);
                refresh = true;
            }
            balanceCache.put(uuid, entry);
        }
        if (refresh) {
            refreshBalance(uuid);
        }
        return entry.balance();
    }

    @Override
    public void giveBalance(@NotNull Player player, double amount) {
        long credits = toCredits(amount);
        UUID uuid = player.getUniqueId();
        service.credit(uuid, credits, UUID.randomUUID().toString(), "allium-plugin-credit")
                .thenAccept(result -> updateCachedBalance(uuid, result.balance()))
                .exceptionally(error -> {
                    logFailure("credit", uuid, error);
                    return null;
                });
    }

    @Override
    public void takeBalance(@NotNull Player player, double amount) {
        long credits = toCredits(amount);
        UUID uuid = player.getUniqueId();
        debit(uuid, credits, UUID.randomUUID().toString())
                .thenAccept(debited -> {
                    if (!debited) {
                        Bukkit.getLogger().warning("Allium debit failed: insufficient balance for " + uuid);
                    }
                })
                .exceptionally(error -> {
                    logFailure("debit", uuid, error);
                    return null;
                });
    }

    public CompletableFuture<Boolean> debit(UUID playerUuid, long amount, String idempotencyKey) {
        if (playerUuid == null || amount <= 0 || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid Allium debit request."));
        }
        return service.debit(playerUuid, amount, idempotencyKey, "axrankmenu-rank-purchase").thenApply(result -> {
            if (result.balance() >= 0) {
                updateCachedBalance(playerUuid, result.balance());
            }
            return result.applied();
        });
    }

    private void refreshBalance(UUID playerUuid) {
        if (pendingLookups.size() >= MAX_PENDING_BALANCE_LOOKUPS || !pendingLookups.add(playerUuid)) {
            return;
        }
        service.getBalance(playerUuid).whenComplete((balance, error) -> {
            pendingLookups.remove(playerUuid);
            if (error != null) {
                logFailure("balance lookup", playerUuid, error);
                return;
            }
            updateCachedBalance(playerUuid, balance);
        });
    }

    private void updateCachedBalance(UUID playerUuid, long balance) {
        synchronized (balanceCache) {
            balanceCache.put(playerUuid, new BalanceCacheEntry(balance, System.currentTimeMillis()));
        }
    }

    private long toCredits(double amount) {
        if (!Double.isFinite(amount) || amount <= 0 || amount > Long.MAX_VALUE
                || amount != Math.rint(amount)) {
            throw new IllegalArgumentException("Allium amounts must be positive whole numbers.");
        }
        return (long) amount;
    }

    private void logFailure(String operation, UUID playerUuid, Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        AxRankMenu.getInstance().getLogger().log(
                Level.SEVERE, "Allium " + operation + " failed for " + playerUuid + ".", cause);
    }

    private record BalanceCacheEntry(long balance, long lastAttemptAt) {}
}
