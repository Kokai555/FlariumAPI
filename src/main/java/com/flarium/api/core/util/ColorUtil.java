package com.flarium.api.core.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;

public class ColorUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Cache<String, Component> FORMAT_CACHE = Caffeine.newBuilder().maximumSize(1000).build();
    private static final Cache<String, String> MINIMESSAGE_CACHE = Caffeine.newBuilder().maximumSize(1000).build();

    public static Component format(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        return FORMAT_CACHE.get(text, ColorUtil::computeFormat);
    }

    private static Component computeFormat(String text) {
        return MINI_MESSAGE.deserialize(toMiniMessage(text))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String toMiniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        return MINIMESSAGE_CACHE.get(text, ColorUtil::computeMiniMessage);
    }

    private static String computeMiniMessage(String text) {
        // C119: single-pass scan. '&&' is a literal '&' escape. A '&' code is only
        // recognized at the start of the string, after a non-alphanumeric, or chained
        // directly after another code (e.g. &a&l), so ordinary text like "R&D" survives.
        StringBuilder out = new StringBuilder(text.length());
        boolean prevWasCode = false;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == '&') {
                    out.append('&');
                    i += 2;
                    prevWasCode = false;
                    continue;
                }
                if (next == '#' && isHexColor(text, i + 2)) {
                    if (isCodeStart(text, i, prevWasCode)) {
                        out.append("<#").append(text, i + 2, i + 8).append('>');
                        i += 8;
                        prevWasCode = true;
                        continue;
                    }
                } else if (isLegacyCode(next)) {
                    if (isCodeStart(text, i, prevWasCode)) {
                        out.append(mapLegacyToMiniMessage(Character.toLowerCase(next)));
                        i += 2;
                        prevWasCode = true;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
            prevWasCode = false;
        }
        return out.toString();
    }

    private static boolean isCodeStart(String text, int ampIndex, boolean prevWasCode) {
        if (ampIndex == 0 || prevWasCode) {
            return true;
        }
        char prev = text.charAt(ampIndex - 1);
        return !((prev >= '0' && prev <= '9') || (prev >= 'a' && prev <= 'z') || (prev >= 'A' && prev <= 'Z'));
    }

    private static boolean isLegacyCode(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f') || (c >= 'k' && c <= 'o') || c == 'r'
                || (c >= 'A' && c <= 'F') || (c >= 'K' && c <= 'O') || c == 'R';
    }

    private static boolean isHexColor(String text, int start) {
        if (start + 6 > text.length()) {
            return false;
        }
        for (int j = start; j < start + 6; j++) {
            char h = text.charAt(j);
            if (!((h >= '0' && h <= '9') || (h >= 'a' && h <= 'f') || (h >= 'A' && h <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    public static List<Component> format(List<String> list) {
        List<Component> result = new ArrayList<>();
        if (list == null || list.isEmpty()) {
            return result;
        }
        for (String line : list) {
            result.add(format(line));
        }
        return result;
    }

    private static String mapLegacyToMiniMessage(char code) {
        return switch (code) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> "&" + code;
        };
    }
}