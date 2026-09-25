package com.artillexstudios.axrankmenu.hooks.currency;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static com.artillexstudios.axrankmenu.AxRankMenu.CONFIG;

public class UltraEconomyHook implements CurrencyHook {
    private Object currency = null;

    @Override
    public void setup() {
        try {
            Object api = Class.forName("me.TechsCode.UltraEconomy.UltraEconomy").getMethod("getAPI").invoke(null);
            Object currencies = api.getClass().getMethod("getCurrencies").invoke(api);
            Object optionalCurrency = currencies.getClass().getMethod("name", String.class).invoke(currencies, CONFIG.getString("hooks.UltraEconomy.currency-name", "coins"));
            Optional<?> currencyOptional = (Optional<?>) optionalCurrency;
            if (currencyOptional.isEmpty()) throw new RuntimeException("Currency not found!");
            currency = currencyOptional.get();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to initialize UltraEconomy hook", e);
        }
    }

    @Override
    public String getName() {
        return "UltraEconomy";
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public double getBalance(@NotNull Player p) {
        try {
            Object api = Class.forName("me.TechsCode.UltraEconomy.UltraEconomy").getMethod("getAPI").invoke(null);
            Object accounts = api.getClass().getMethod("getAccounts").invoke(api);
            Object optionalAccount = accounts.getClass().getMethod("uuid", UUID.class).invoke(accounts, p.getUniqueId());
            Optional<?> accountOptional = (Optional<?>) optionalAccount;
            if (accountOptional.isEmpty()) return 0.0D;
            Object account = accountOptional.get();
            Method getBalance = account.getClass().getMethod("getBalance", currency.getClass());
            Object balance = getBalance.invoke(account, currency);
            return (double) balance.getClass().getMethod("getOnHand").invoke(balance);
        } catch (ReflectiveOperationException e) {
            return 0.0D;
        }
    }

    @Override
    public void giveBalance(@NotNull Player p, double amount) {
        try {
            Object api = Class.forName("me.TechsCode.UltraEconomy.UltraEconomy").getMethod("getAPI").invoke(null);
            Object accounts = api.getClass().getMethod("getAccounts").invoke(api);
            Object optionalAccount = accounts.getClass().getMethod("uuid", UUID.class).invoke(accounts, p.getUniqueId());
            Optional<?> accountOptional = (Optional<?>) optionalAccount;
            if (accountOptional.isEmpty()) return;
            Object account = accountOptional.get();
            Method addBalance = account.getClass().getMethod("addBalance", currency.getClass(), double.class);
            addBalance.invoke(account, currency, amount);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Override
    public void takeBalance(@NotNull Player p, double amount) {
        try {
            Object api = Class.forName("me.TechsCode.UltraEconomy.UltraEconomy").getMethod("getAPI").invoke(null);
            Object accounts = api.getClass().getMethod("getAccounts").invoke(api);
            Object optionalAccount = accounts.getClass().getMethod("uuid", UUID.class).invoke(accounts, p.getUniqueId());
            Optional<?> accountOptional = (Optional<?>) optionalAccount;
            if (accountOptional.isEmpty()) return;
            Object account = accountOptional.get();
            Method removeBalance = account.getClass().getMethod("removeBalance", currency.getClass(), double.class);
            removeBalance.invoke(account, currency, amount);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}