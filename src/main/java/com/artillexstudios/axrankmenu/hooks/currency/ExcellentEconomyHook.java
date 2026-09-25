package com.artillexstudios.axrankmenu.hooks.currency;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

import static com.artillexstudios.axrankmenu.AxRankMenu.CONFIG;

public class ExcellentEconomyHook implements CurrencyHook {
    private Object api;
    private Object currency = null;

    @Override
    public void setup() {
        try {
            Class<?> apiClass = Class.forName("su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI");
            for (RegisteredServiceProvider<?> registration : Bukkit.getServer().getServicesManager().getRegistrations(apiClass)) {
                api = registration.getProvider();
                break;
            }
            if (api == null) {
                throw new IllegalStateException("ExcellentEconomy API not registered");
            }
            Method getCurrency = apiClass.getMethod("getCurrency", String.class);
            currency = getCurrency.invoke(api, CONFIG.getString("hooks.ExcellentEconomy.currency-name", "coins"));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to initialize ExcellentEconomy hook", e);
        }
    }

    @Override
    public String getName() {
        return "ExcellentEconomy";
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public double getBalance(@NotNull Player p) {
        if (currency == null) return 0;
        try {
            Class<?> currencyClass = Class.forName("su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency");
            Method getBalance = api.getClass().getMethod("getBalance", Player.class, currencyClass);
            return (double) getBalance.invoke(api, p, currency);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    @Override
    public void giveBalance(@NotNull Player p, double amount) {
        if (currency == null) return;
        try {
            Class<?> currencyClass = Class.forName("su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency");
            Method deposit = api.getClass().getMethod("deposit", Player.class, currencyClass, double.class);
            deposit.invoke(api, p, currency, amount);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Override
    public void takeBalance(@NotNull Player p, double amount) {
        if (currency == null) return;
        try {
            Class<?> currencyClass = Class.forName("su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency");
            Method withdraw = api.getClass().getMethod("withdraw", Player.class, currencyClass, double.class);
            withdraw.invoke(api, p, currency, amount);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}