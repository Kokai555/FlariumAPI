package com.flarium.api.feature.currency;

import com.flarium.api.core.util.ColorUtil;
import net.kyori.adventure.text.Component;

import java.math.BigDecimal;

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
        // C49: plain decimal notation, no scientific notation for large amounts.
        String amountStr = allowDecimals ? BigDecimal.valueOf(amount).toPlainString() : String.valueOf(Math.round(amount));
        String formatted = displayFormat.replace("%amount%", amountStr);
        return ColorUtil.format(formatted);
    }
}