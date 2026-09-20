package com.flarium.api.feature.currency;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CurrencyManager {

    private final Map<String, Currency> currencies = new ConcurrentHashMap<>();

    /**
     * C51: bounded per-key stripe locks serialising check-and-take for the same
     * player/currency. Striped (fixed size, no per-player allocation/leak);
     * different players usually land on different stripes and stay concurrent.
     * Works on any thread model (Bukkit/Folia): short critical sections only,
     * no scheduler or dispatch changes (C52 untouched).
     */
    private final Object[] balanceStripes = new Object[64];

    public CurrencyManager() {
        for (int i = 0; i < balanceStripes.length; i++) {
            balanceStripes[i] = new Object();
        }
    }

    public void loadCurrencies(ConfigurationSection section) {
        currencies.clear();
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection currencySection = section.getConfigurationSection(key);
            if (currencySection == null) continue;

            boolean enabled = currencySection.getBoolean("enabled", false);
            if (!enabled) continue;

            Currency currency = new Currency(
                    key,
                    true,
                    currencySection.getString("display-format", "%amount%"),
                    currencySection.getBoolean("allow-decimals", false),
                    currencySection.getBoolean("works-offline", false),
                    currencySection.getString("options.placeholder", "%vault_eco_balance%"),
                    currencySection.getString("options.give-command", "eco give %player% %amount%"),
                    currencySection.getString("options.take-command", "eco take %player% %amount%")
            );
            currencies.put(key.toLowerCase(), currency);
        }
    }

    public double getBalance(Player player, String currencyId) {
        Currency currency = currencies.get(currencyId.toLowerCase());
        if (currency == null) return 0.0;

        String balanceStr = PlaceholderAPI.setPlaceholders(player, currency.placeholder());
        try {
            return Double.parseDouble(balanceStr);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public boolean hasEnough(Player player, String currencyId, double amount) {
        return getBalance(player, currencyId) >= amount;
    }

    public void give(Player player, String currencyId, double amount) {
        Currency currency = currencies.get(currencyId.toLowerCase());
        if (currency == null) throw new IllegalArgumentException("Unknown or disabled currency: " + currencyId);
        if (!Double.isFinite(amount) || amount <= 0) throw new IllegalArgumentException("Amount must be a positive finite number: " + amount);

        String amountStr = formatAmount(currency, amount);
        String command = currency.giveCommand()
                .replace("%player%", player.getName())
                .replace("%amount%", amountStr);

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    public void take(Player player, String currencyId, double amount) {
        Currency currency = currencies.get(currencyId.toLowerCase());
        if (currency == null) throw new IllegalArgumentException("Unknown or disabled currency: " + currencyId);
        if (!Double.isFinite(amount) || amount <= 0) throw new IllegalArgumentException("Amount must be a positive finite number: " + amount);

        // C51: serialise dispatches per player/currency. Fire-and-forget semantics
        // preserved (no new balance check here); the atomic check-then-act
        // primitive is takeIfEnough(...) below.
        synchronized (lockFor(player, currencyId)) {
            String amountStr = formatAmount(currency, amount);
            String command = currency.takeCommand()
                    .replace("%player%", player.getName())
                    .replace("%amount%", amountStr);

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    /**
     * C51: atomic check-and-take for one player/currency.
     *
     * <p>Re-reads the balance inside the per-key lock and dispatches the take
     * command only when funds suffice. Returns {@code true} when the take was
     * dispatched, {@code false} when the balance is insufficient. Concurrent
     * callers racing on the same player/currency cannot both succeed against
     * funds that cover only one of them. C50 validation (unknown/disabled
     * currency, positive finite amount) is identical to {@link #take}.</p>
     */
    public boolean takeIfEnough(Player player, String currencyId, double amount) {
        Currency currency = currencies.get(currencyId.toLowerCase());
        if (currency == null) throw new IllegalArgumentException("Unknown or disabled currency: " + currencyId);
        if (!Double.isFinite(amount) || amount <= 0) throw new IllegalArgumentException("Amount must be a positive finite number: " + amount);

        synchronized (lockFor(player, currencyId)) {
            if (!hasEnough(player, currencyId, amount)) return false;

            String amountStr = formatAmount(currency, amount);
            String command = currency.takeCommand()
                    .replace("%player%", player.getName())
                    .replace("%amount%", amountStr);

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return true;
        }
    }

    /**
     * C49: decimal representation for the external economy command without
     * scientific notation. {@code BigDecimal.valueOf(...).toPlainString()}
     * renders the exact decimal value (no rounding, no exponent); the integer
     * path was already exponent-free and keeps byte-identical output via
     * {@code Long.toString}. External command syntax unchanged.
     */
    static String formatAmount(Currency currency, double amount) {
        if (currency.allowDecimals()) {
            return BigDecimal.valueOf(amount).toPlainString();
        }
        return Long.toString(Math.round(amount));
    }

    private Object lockFor(Player player, String currencyId) {
        String key = player.getUniqueId() + ":" + currencyId.toLowerCase();
        return balanceStripes[(key.hashCode() & 0x7fffffff) % balanceStripes.length];
    }
}