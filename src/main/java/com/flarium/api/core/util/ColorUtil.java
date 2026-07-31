package com.flarium.api.core.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
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
        Matcher hexMatcher = HEX_PATTERN.matcher(text);
        StringBuilder hexBuffer = new StringBuilder();
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(hexBuffer, Matcher.quoteReplacement("<#" + hexMatcher.group(1) + ">"));
        }
        hexMatcher.appendTail(hexBuffer);
        String hexProcessed = hexBuffer.toString();

        Matcher legacyMatcher = LEGACY_PATTERN.matcher(hexProcessed);
        StringBuilder legacyBuffer = new StringBuilder();
        while (legacyMatcher.find()) {
            char code = legacyMatcher.group(1).toLowerCase().charAt(0);
            String replacement = mapLegacyToMiniMessage(code);
            legacyMatcher.appendReplacement(legacyBuffer, Matcher.quoteReplacement(replacement));
        }
        legacyMatcher.appendTail(legacyBuffer);
        return legacyBuffer.toString();
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