package com.flarium.api.feature.currency;

import com.flarium.api.core.util.ColorUtil;
import net.kyori.adventure.text.Component;

public record Currency(
        String id,
        boolean enabled,
        String displayFormat,
        boolean allowDecimals,
        boolean worksOffline,
        String placeholder,
        String giveCommand,
        String takeCommand
) {
    public Component formatDisplay(double amount) {
        String amountStr = allowDecimals ? String.valueOf(amount) : String.valueOf(Math.round(amount));
        String formatted = displayFormat.replace("%amount%", amountStr);
        return ColorUtil.format(formatted);
    }
}