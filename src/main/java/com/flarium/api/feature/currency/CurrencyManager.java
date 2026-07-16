package com.flarium.api.feature.currency;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class CurrencyManager {

    private final Map<String, Currency> currencies = new HashMap<>();

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
        if (currency == null) return;

        String amountStr = currency.allowDecimals() ? String.valueOf(amount) : String.valueOf((long) Math.round(amount));
        String command = currency.giveCommand()
                .replace("%player%", player.getName())
                .replace("%amount%", amountStr);

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    public void take(Player player, String currencyId, double amount) {
        Currency currency = currencies.get(currencyId.toLowerCase());
        if (currency == null) return;

        String amountStr = currency.allowDecimals() ? String.valueOf(amount) : String.valueOf((long) Math.round(amount));
        String command = currency.takeCommand()
                .replace("%player%", player.getName())
                .replace("%amount%", amountStr);

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}