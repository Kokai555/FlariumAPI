package com.flarium.api.ui.menu.structure;

import java.util.*;

public class Structure {

    private final int width;
    private final int height;
    private final String[] lines;
    private final Map<Character, List<Integer>> charMap = new HashMap<>();

    public Structure(String... lines) {
        if (lines == null || lines.length == 0) {
            this.width = 0;
            this.height = 0;
            this.lines = new String[0];
            return;
        }

        int expectedLength = lines[0].length();
        for (String row : lines) {
            if (row.length() != expectedLength) {
                throw new IllegalArgumentException("Structure rows must have the same length!");
            }
        }

        this.height = lines.length;
        this.width = lines[0].length();
        this.lines = lines;

        for (int y = 0; y < height; y++) {
            String line = lines[y];
            for (int x = 0; x < line.length(); x++) {
                char c = line.charAt(x);
                if (c == ' ' || c == '.') continue;

                int slot = y * 9 + x;
                charMap.computeIfAbsent(c, k -> new ArrayList<>()).add(slot);
            }
        }
    }

    public List<Integer> getSlots(char character) {
        return charMap.getOrDefault(character, Collections.emptyList());
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String[] getLines() { return lines; }
}