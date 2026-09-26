package com.artillexstudios.axrankmenu.rank;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletionException;

public final class PurchaseRecoveryStore {
    public record PendingPurchase(long amount, String idempotencyKey) {}

    private final Path file;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "AxRankMenu-PurchaseRecovery");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, PendingPurchase> pending = new HashMap<>();
    private final CompletableFuture<Void> ready;

    public PurchaseRecoveryStore(Path file) {
        this.file = file;
        this.ready = CompletableFuture.runAsync(this::load, executor);
    }

    public CompletableFuture<PendingPurchase> getOrCreate(
            UUID playerUuid, String currency, String rank, long amount) {
        String key = recordKey(playerUuid, currency, rank);
        return ready.thenCompose(ignored -> CompletableFuture.supplyAsync(() -> {
            PendingPurchase existing = pending.get(key);
            if (existing != null) {
                if (existing.amount() != amount) {
                    throw new CompletionException(new IllegalStateException(
                            "A previous purchase for this player/rank has an unresolved different amount."));
                }
                return existing;
            }
            PendingPurchase created = new PendingPurchase(amount, UUID.randomUUID().toString());
            pending.put(key, created);
            try {
                save();
                return created;
            } catch (IOException error) {
                pending.remove(key);
                throw new CompletionException(error);
            }
        }, executor));
    }

    public CompletableFuture<Void> clear(UUID playerUuid, String currency, String rank) {
        String key = recordKey(playerUuid, currency, rank);
        return ready.thenCompose(ignored -> CompletableFuture.runAsync(() -> {
            PendingPurchase previous = pending.remove(key);
            if (previous == null) return;
            try {
                save();
            } catch (IOException error) {
                pending.put(key, previous);
                throw new CompletionException(error);
            }
        }, executor));
    }

    public void shutdown() {
        executor.shutdown();
    }

    private void load() {
        if (!Files.exists(file)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException error) {
            throw new CompletionException(new IOException("Could not load pending rank purchases.", error));
        }
        for (String property : properties.stringPropertyNames()) {
            if (!property.startsWith("purchase.")) continue;
            String[] values = properties.getProperty(property, "").split("\\|", 2);
            if (values.length != 2 || values[1].isBlank() || values[1].length() > 128) {
                throw new CompletionException(new IOException("Invalid pending purchase record in " + file));
            }
            try {
                long amount = Long.parseLong(values[0]);
                if (amount <= 0) throw new NumberFormatException("amount must be positive");
                pending.put(property.substring("purchase.".length()),
                        new PendingPurchase(amount, values[1]));
            } catch (NumberFormatException error) {
                throw new CompletionException(new IOException("Invalid pending purchase amount in " + file, error));
            }
        }
    }

    private void save() throws IOException {
        Files.createDirectories(file.getParent());
        Properties properties = new Properties();
        pending.forEach((key, purchase) -> properties.setProperty(
                "purchase." + key, purchase.amount() + "|" + purchase.idempotencyKey()));
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "Pending currency-backed rank purchases");
        }
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String recordKey(UUID playerUuid, String currency, String rank) {
        if (playerUuid == null || currency == null || currency.isBlank() || rank == null || rank.isBlank()) {
            throw new IllegalArgumentException("Player, currency, and rank are required for purchase recovery.");
        }
        String identity = playerUuid + "|" + currency.toLowerCase(java.util.Locale.ROOT)
                + "|" + rank.toLowerCase(java.util.Locale.ROOT);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
