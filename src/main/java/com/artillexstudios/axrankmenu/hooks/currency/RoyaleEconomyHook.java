package com.artillexstudios.axrankmenu.hooks.currency;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class RoyaleEconomyHook implements CurrencyHook {
    private Object balanceApi = null;

    @Override
    public void setup() {
        try {
            Class<?> royaleEconomyClass = Class.forName("me.qKing12.RoyaleEconomy.RoyaleEconomy");
            Field apiHandler = royaleEconomyClass.getField("apiHandler");
            Object handler = apiHandler.get(null);
            Field balance = handler.getClass().getField("balance");
            balanceApi = balance.get(handler);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to initialize RoyaleEconomy hook", e);
        }
    }

    @Override
    public String getName() {
        return "RoyaleEconomy";
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public double getBalance(@NotNull Player p) {
        if (balanceApi == null) return 0;
        try {
            Method getBalance = balanceApi.getClass().getMethod("getBalance", String.class);
            return (double) getBalance.invoke(balanceApi, p.getUniqueId().toString());
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    @Override
    public void giveBalance(@NotNull Player p, double amount) {
        if (balanceApi == null) return;
        try {
            Method addBalance = balanceApi.getClass().getMethod("addBalance", String.class, double.class);
            addBalance.invoke(balanceApi, p.getUniqueId().toString(), amount);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Override
    public void takeBalance(@NotNull Player p, double amount) {
        if (balanceApi == null) return;
        try {
            Method removeBalance = balanceApi.getClass().getMethod("removeBalance", String.class, double.class);
            removeBalance.invoke(balanceApi, p.getUniqueId().toString(), amount);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}