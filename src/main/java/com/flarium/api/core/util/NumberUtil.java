package com.flarium.api.core.util;

import java.text.NumberFormat;
import java.util.Locale;

public class NumberUtil {

    private static final NumberFormat COMMA_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final NumberFormat COMPACT_FORMAT = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);

    static {
        COMMA_FORMAT.setGroupingUsed(true);
        COMPACT_FORMAT.setMaximumFractionDigits(2);
    }

    private NumberUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String formatCommas(long number) {
        return COMMA_FORMAT.format(number);
    }

    public static String formatShort(double number) {
        return COMPACT_FORMAT.format(number).replace("K", "k");
    }
}