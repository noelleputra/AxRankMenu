package com.artillexstudios.axrankmenu.rank;

import com.artillexstudios.axrankmenu.AxRankMenu;
import com.artillexstudios.axapi.libs.boostedyaml.block.implementation.Section;
import com.artillexstudios.axapi.libs.boostedyaml.settings.general.GeneralSettings;
import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.utils.NumberUtils;
import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axrankmenu.hooks.HookManager;
import com.artillexstudios.axrankmenu.hooks.currency.AlliumHook;
import com.artillexstudios.axrankmenu.hooks.currency.CurrencyHook;
import com.artillexstudios.axrankmenu.hooks.currency.VotePointsHook;
import com.artillexstudios.axrankmenu.utils.ItemBuilderUtil;
import com.artillexstudios.axrankmenu.utils.PlaceholderUtils;
import dev.triumphteam.gui.guis.GuiItem;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import net.luckperms.api.track.Track;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.artillexstudios.axrankmenu.AxRankMenu.CONFIG;
import static com.artillexstudios.axrankmenu.AxRankMenu.LANG;
import static com.artillexstudios.axrankmenu.AxRankMenu.MESSAGEUTILS;
import static com.artillexstudios.axrankmenu.AxRankMenu.RANKS;

public class Rank {
    private static final Set<UUID> REMOTE_CURRENCY_PURCHASES = ConcurrentHashMap.newKeySet();
    private static final LuckPerms luckPerms = LuckPermsProvider.get();
    private final Group group;
    private final Section section;
    private final Player requester;

    public Rank(@NotNull Section section, @NotNull Player requester) {
        this.section = section;
        this.requester = requester;
        group = luckPerms.getGroupManager().getGroup(section.getString("rank"));
    }

    public Node[] getNodes() {
        return group.getNodes().stream().filter(node -> !node.isNegated()).toArray(Node[]::new);
    }

    public GuiItem getItem() {
        final List<String> lore = new ArrayList<>();
        for (String line : section.getStringList("item.lore")) {
            if (line.contains("%permission%")) {
                LANG.getBackingDocument().setGeneralSettings(GeneralSettings.builder().setRouteSeparator('倀').build());
                for (Node node : getNodes()) {
                    ImmutableContextSet set = luckPerms.getContextManager().getStaticContext();
                    if (!section.getString("server", "").isEmpty()) {
                        set = ImmutableContextSet.of("server", section.getString("server"));
                    }

                    if (!CONFIG.getBoolean("include-global-permissions") && !node.getContexts().equals(set)) continue;
                    if (CONFIG.getBoolean("include-global-permissions") && !node.getContexts().isEmpty() && !node.getContexts().equals(set)) continue;
                    String permission = node.getKey();

                    Integer number = null;
                    for (String t1 : permission.split("\\.")) {
                        if (!NumberUtils.isInt(t1)) continue;
                        number = Integer.parseInt(t1);
                    }

                    permission = permission.replace("" + number, "#");

                    if (LANG.getString("permissions倀" + permission) == null) {
                        LANG.set("permissions倀" + permission, permission);
                        LANG.save();
                    }

                    String tName = LANG.getString("permissions倀" + permission);
                    if (tName.isEmpty()) continue;
                    lore.add(PlaceholderUtils.parsePlaceholders(requester, line.replace("%permission%", tName.replace("#", "" + number)), section));
                }
                LANG.getBackingDocument().setGeneralSettings(GeneralSettings.builder().setRouteSeparator('.').build());
            } else {
                lore.add(PlaceholderUtils.parsePlaceholders(requester, line, section));
            }
        }

        final ItemStack it = ItemBuilderUtil.newBuilder(section.getSection("item"), requester).setLore(lore).get();

        return new GuiItem(it, event -> {
            final String cGroupName = luckPerms.getUserManager().getUser(requester.getUniqueId()).getPrimaryGroup();
            final Group cGroup = luckPerms.getGroupManager().getGroup(cGroupName);
            if (CONFIG.getBoolean("prevent-downgrading", true) && cGroup.getWeight().isPresent() && group.getWeight().isPresent() && group.getWeight().getAsInt() <= cGroup.getWeight().getAsInt()) {
                MESSAGEUTILS.sendLang(requester, "error.downgrade-disabled");
                return;
            }

            if (CONFIG.getBoolean("force-buy-order.enabled", false)) {
                final Track track = luckPerms.getTrackManager().getTrack(CONFIG.getString("force-buy-order.track"));
                final String nextGroup = track.getNext(cGroup);
                if (nextGroup == null && !track.containsGroup(cGroup) && !track.getGroups().get(0).equals(group.getName())) {
                    MESSAGEUTILS.sendLang(requester, "error.buy-order");
                    return;
                }
                if (nextGroup != null && !group.getName().equals(nextGroup)) {
                    MESSAGEUTILS.sendLang(requester, "error.buy-order");
                    return;
                }
            }

            double price = section.getDouble("price", -1.0D);
            final String currency = section.getString("currency", "Vault");

            if (price == -1) return;

            if (CONFIG.getBoolean("discount-ranks", false)) {
                double currentPrice = Math.max(RANKS.getDouble(cGroup.getName() + ".price", -1), RANKS.getDouble(cGroup.getName().toUpperCase() + ".price", -1));
                if (currentPrice != -1) {
                    price -= currentPrice;
                    price = Math.max(0, price);
                }
            }

            final CurrencyHook hook = HookManager.getCurrencyHook(currency);
            if (hook == null) return;

            if (hook instanceof VotePointsHook votePointsHook) {
                purchaseWithAtomicDebit("VotePoints", price, amount -> votePointsHook.debit(
                        requester.getUniqueId(), amount, UUID.randomUUID().toString()));
                return;
            }
            if (hook instanceof AlliumHook alliumHook) {
                purchaseWithAtomicDebit("Allium", price, amount -> alliumHook.debit(
                        requester.getUniqueId(), amount, UUID.randomUUID().toString()));
                return;
            }

            if (hook.getBalance(requester) < price) {
                MESSAGEUTILS.sendLang(requester, "buy.no-currency");
                return;
            }

            hook.takeBalance(requester, price);
            executeBuyActions(requester.getUniqueId(), requester.getName(), price);
        });
    }

    private void purchaseWithAtomicDebit(
            String currencyName,
            double price,
            java.util.function.LongFunction<java.util.concurrent.CompletableFuture<Boolean>> debitRequest) {
        if (!Double.isFinite(price) || price < 0 || price > Long.MAX_VALUE || price != Math.rint(price)) {
            MESSAGEUTILS.sendLang(requester, "buy.no-currency");
            return;
        }
        UUID playerUuid = requester.getUniqueId();
        String playerName = requester.getName();
        if (price == 0) {
            executeBuyActions(playerUuid, playerName, price);
            return;
        }
        if (!REMOTE_CURRENCY_PURCHASES.add(playerUuid)) {
            MESSAGEUTILS.sendLang(requester, "buy.no-currency");
            return;
        }
        try {
            debitRequest.apply((long) price).whenComplete((debited, error) ->
                    Scheduler.get().run(requester, task -> {
                            REMOTE_CURRENCY_PURCHASES.remove(playerUuid);
                            if (error != null) {
                                Throwable cause = error;
                                while (cause instanceof java.util.concurrent.CompletionException
                                        && cause.getCause() != null) {
                                    cause = cause.getCause();
                                }
                                AxRankMenu.getInstance().getLogger().log(
                                        java.util.logging.Level.WARNING,
                                        currencyName + " rank purchase could not be confirmed for " + playerUuid
                                                + "; no rank actions were run.",
                                        cause);
                                Player online = Bukkit.getPlayer(playerUuid);
                                if (online != null) {
                                    MESSAGEUTILS.sendLang(online, "buy.no-currency");
                                }
                            } else if (Boolean.TRUE.equals(debited)) {
                                executeBuyActions(playerUuid, playerName, price);
                            } else {
                                Player online = Bukkit.getPlayer(playerUuid);
                                if (online != null) {
                                    MESSAGEUTILS.sendLang(online, "buy.no-currency");
                                }
                            }
                        }, () -> {
                            REMOTE_CURRENCY_PURCHASES.remove(playerUuid);
                            if (error != null || Boolean.TRUE.equals(debited)) {
                                AxRankMenu.getInstance().getLogger().warning(
                                        currencyName + " rank purchase finished after " + playerUuid
                                                + " left; check the transaction before retrying.");
                            }
                        }));
        } catch (RuntimeException exception) {
            REMOTE_CURRENCY_PURCHASES.remove(playerUuid);
            AxRankMenu.getInstance().getLogger().log(
                    java.util.logging.Level.SEVERE,
                    currencyName + " rank purchase could not be started for " + playerUuid + ".",
                    exception);
            MESSAGEUTILS.sendLang(requester, "buy.no-currency");
        }
    }

    private void executeBuyActions(UUID playerUuid, String playerName, double price) {
        var actions = section.getStringList("buy-actions");
        if (actions.isEmpty()) {
            Bukkit.getConsoleSender().sendMessage(StringUtils.formatToString(
                    "&#FF0000[AxRankMenu] The buy-actions section is missing from the "
                            + section.getString("rank") + " rank, this will cause issues!"));
        }
        for (String action : actions) {
            final String[] type = action.split(" ", 2);
            if (type.length != 2) {
                continue;
            }
            String formatted = type[1].replace("%player%", playerName)
                    .replace("%name%", section.getString("item.name"))
                    .replace("%rank%", section.getString("rank"))
                    .replace("%price%", section.getString("price", "---"))
                    .replace("%server%", section.getString("server"));
            Player online = Bukkit.getPlayer(playerUuid);
            switch (type[0]) {
                case "[MESSAGE]" -> {
                    if (online != null) {
                        online.sendMessage(StringUtils.formatToString(formatted));
                    }
                }
                case "[CONSOLE]" -> Scheduler.get().execute(
                        () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formatted));
                case "[CLOSE]" -> {
                    if (online != null) {
                        online.closeInventory();
                    }
                }
                default -> {
                }
            }
        }
    }

    @Nullable
    public Group getGroup() {
        return group;
    }

    public Section getSection() {
        return section;
    }
}
