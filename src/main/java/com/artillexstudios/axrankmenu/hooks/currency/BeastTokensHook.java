package com.artillexstudios.axrankmenu.hooks.currency;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

public class BeastTokensHook implements CurrencyHook {
    private Object tokensManager = null;

    @Override
    public void setup() {
        try {
            Class<?> apiClass = Class.forName("me.mraxetv.beasttokens.api.BeastTokensAPI");
            Method getTokensManager = apiClass.getMethod("getTokensManager");
            tokensManager = getTokensManager.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to initialize BeastTokens hook", e);
        }
    }

    @Override
    public String getName() {
        return "BeastTokens";
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public double getBalance(@NotNull Player p) {
        if (tokensManager == null) return 0;
        try {
            Method getTokens = tokensManager.getClass().getMethod("getTokens", Player.class);
            return (double) getTokens.invoke(tokensManager, p);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    @Override
    public void giveBalance(@NotNull Player p, double amount) {
        if (tokensManager == null) return;
        try {
            Method addTokens = tokensManager.getClass().getMethod("addTokens", Player.class, double.class);
            addTokens.invoke(tokensManager, p, amount);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Override
    public void takeBalance(@NotNull Player p, double amount) {
        if (tokensManager == null) return;
        try {
            Method removeTokens = tokensManager.getClass().getMethod("removeTokens", Player.class, double.class);
            removeTokens.invoke(tokensManager, p, amount);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}