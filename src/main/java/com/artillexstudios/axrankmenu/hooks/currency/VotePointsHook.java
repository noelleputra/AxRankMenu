package com.artillexstudios.axrankmenu.hooks.currency;

import com.artillexstudios.axrankmenu.AxRankMenu;
import dev.noellx.nullaelib.api.VotePointsService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

public final class VotePointsHook implements CurrencyHook {
    private final ConcurrentMap<UUID, Long> balanceCache = new ConcurrentHashMap<>();
    private VotePointsService service;

    @Override
    public void setup() {
        service = Bukkit.getServicesManager().load(VotePointsService.class);
        if (service == null) {
            throw new IllegalStateException("Votifae Vote Points service is unavailable.");
        }
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
        Bukkit.getLogger().warning("VotePoints cannot be credited through AxRankMenu; "
                + "points are awarded by valid Votifae votes.");
    }

    @Override
    public void takeBalance(@NotNull Player player, double amount) {
        long points = toPoints(amount);
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
        return service.getBalance(playerUuid).thenApply(balance -> {
            balanceCache.put(playerUuid, balance);
            return balance;
        });
    }

    public CompletableFuture<Boolean> debit(UUID playerUuid, long amount, String idempotencyKey) {
        if (amount <= 0 || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Vote point debit requires a positive amount and idempotency key.");
        }
        return service.debit(playerUuid, amount, idempotencyKey).thenApply(result -> {
            balanceCache.put(playerUuid, result.balance());
            return result.debited();
        });
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
