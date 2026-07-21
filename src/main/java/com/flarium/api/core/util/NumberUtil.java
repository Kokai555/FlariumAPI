package com.flarium.api.core.util;

import java.text.NumberFormat;
import java.util.Locale;

public class NumberUtil {

    private static final ThreadLocal<NumberFormat> COMMA_FORMAT =
            ThreadLocal.withInitial(() -> NumberFormat.getNumberInstance(Locale.US));

    private static final ThreadLocal<NumberFormat> COMPACT_FORMAT =
            ThreadLocal.withInitial(() -> {
                NumberFormat format = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);
                format.setMaximumFractionDigits(2);
                return format;
            });

    private NumberUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String formatCommas(long number) {
        return COMMA_FORMAT.get().format(number);
    }

    public static String formatShort(double number) {
        return COMPACT_FORMAT.get().format(number).replace("K", "k");
    }
}