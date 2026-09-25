package com.artillexstudios.axrankmenu.hooks.currency;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

import static com.artillexstudios.axrankmenu.AxRankMenu.CONFIG;

public class CoinsEngineHook implements CurrencyHook {
    private Object currency = null;

    @Override
    public void setup() {
        try {
            Class<?> apiClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            Method getCurrency = apiClass.getMethod("getCurrency", String.class);
            currency = getCurrency.invoke(null, CONFIG.getString("hooks.CoinsEngine.currency-name", "coins"));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to initialize CoinsEngine hook", e);
        }
    }

    @Override
    public String getName() {
        return "CoinsEngine";
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public double getBalance(@NotNull Player p) {
        if (currency == null) return 0;
        try {
            Class<?> apiClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            Class<?> currencyClass = Class.forName("su.nightexpress.coinsengine.api.currency.Currency");
            Method getBalance = apiClass.getMethod("getBalance", Player.class, currencyClass);
            return (double) getBalance.invoke(null, p, currency);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    @Override
    public void giveBalance(@NotNull Player p, double amount) {
        if (currency == null) return;
        try {
            Class<?> apiClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            Class<?> currencyClass = Class.forName("su.nightexpress.coinsengine.api.currency.Currency");
            Method addBalance = apiClass.getMethod("addBalance", Player.class, currencyClass, double.class);
            addBalance.invoke(null, p, currency, amount);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Override
    public void takeBalance(@NotNull Player p, double amount) {
        if (currency == null) return;
        try {
            Class<?> apiClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            Class<?> currencyClass = Class.forName("su.nightexpress.coinsengine.api.currency.Currency");
            Method removeBalance = apiClass.getMethod("removeBalance", Player.class, currencyClass, double.class);
            removeBalance.invoke(null, p, currency, amount);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}