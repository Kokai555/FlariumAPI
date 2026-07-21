package com.flarium.api.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    public static Component format(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        Matcher hexMatcher = HEX_PATTERN.matcher(text);
        StringBuilder hexBuffer = new StringBuilder();
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(hexBuffer, Matcher.quoteReplacement("<#" + hexMatcher.group(1) + ">"));
        }
        hexMatcher.appendTail(hexBuffer);
        String hexProcessed = hexBuffer.toString();

        Matcher legacyMatcher = LEGACY_PATTERN.matcher(hexProcessed);
        StringBuffer legacyBuffer = new StringBuffer();
        while (legacyMatcher.find()) {
            char code = legacyMatcher.group(1).toLowerCase().charAt(0);
            String replacement = mapLegacyToMiniMessage(code);
            legacyMatcher.appendReplacement(legacyBuffer, Matcher.quoteReplacement(replacement));
        }
        legacyMatcher.appendTail(legacyBuffer);
        String fullyProcessed = legacyBuffer.toString();

        return MINI_MESSAGE.deserialize(fullyProcessed);
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