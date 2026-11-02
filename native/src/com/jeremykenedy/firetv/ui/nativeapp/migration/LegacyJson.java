package com.jeremykenedy.firetv.ui.nativeapp.migration;

import java.util.*;

/** Bounded JSON input with duplicate-key rejection on Android as well as the host. */
public final class LegacyJson {
    private final String text;
    private int position, nodes;
    private LegacyJson(String text) {
        if (text == null || text.length() > 1024 * 1024) throw invalid();
        this.text = text;
    }
    public static Map<String, Object> object(String text) {
        LegacyJson parser = new LegacyJson(text);
        Object value = parser.value(0);
        parser.space();
        if (!(value instanceof Map) || parser.position != text.length()) throw invalid();
        @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }
    private Object value(int depth) {
        if (depth > 32 || ++nodes > 16384) throw invalid();
        space();
        char current = peek();
        if (current == '"') return string();
        if (current == '{') {
            position++; space();
            Map<String, Object> result = new LinkedHashMap<>();
            if (take('}')) return result;
            do {
                space();
                String key = string();
                space(); require(':');
                if (result.containsKey(key)) throw invalid();
                result.put(key, value(depth + 1));
                space();
                if (take('}')) return result;
                require(',');
            } while (true);
        }
        if (current == '[') {
            position++; space();
            List<Object> result = new ArrayList<>();
            if (take(']')) return result;
            do {
                result.add(value(depth + 1)); space();
                if (take(']')) return result;
                require(',');
            } while (true);
        }
        for (String literal : new String[]{"true", "false", "null"}) {
            if (text.startsWith(literal, position)) {
                position += literal.length();
                return literal.equals("null") ? null : literal.equals("true");
            }
        }
        int start = position;
        take('-');
        if (take('0')) {
            if (digit(peek())) throw invalid();
        } else {
            if (peek() < '1' || peek() > '9') throw invalid();
            while (digit(peek())) position++;
        }
        if (take('.')) { if (!digit(peek())) throw invalid(); while (digit(peek())) position++; }
        if (take('e') || take('E')) {
            if (!take('+')) take('-');
            if (!digit(peek())) throw invalid();
            while (digit(peek())) position++;
        }
        try {
            if (position - start > 128) throw invalid();
            java.math.BigDecimal number = new java.math.BigDecimal(text.substring(start, position));
            if (number.scale() < -1024 || number.scale() > 1024) throw invalid();
            return number;
        }
        catch (NumberFormatException bad) { throw invalid(); }
    }
    private String string() {
        require('"');
        StringBuilder value = new StringBuilder();
        while (position < text.length()) {
            char next = text.charAt(position++);
            if (next == '"') return value.toString();
            if (next < 32) throw invalid();
            if (next == '\\') {
                if (position >= text.length()) throw invalid();
                next = text.charAt(position++);
                switch (next) {
                    case '"': case '\\': case '/': break;
                    case 'b': next = '\b'; break;
                    case 'f': next = '\f'; break;
                    case 'n': next = '\n'; break;
                    case 'r': next = '\r'; break;
                    case 't': next = '\t'; break;
                    case 'u':
                        if (position + 4 > text.length()) throw invalid();
                        int number = 0;
                        for (int index = 0; index < 4; index++) {
                            char hex = text.charAt(position++);
                            int digit = hex >= '0' && hex <= '9' ? hex - '0'
                                    : hex >= 'a' && hex <= 'f' ? hex - 'a' + 10
                                    : hex >= 'A' && hex <= 'F' ? hex - 'A' + 10 : -1;
                            if (digit < 0) throw invalid();
                            number = number * 16 + digit;
                        }
                        next = (char) number; break;
                    default: throw invalid();
                }
            }
            value.append(next);
        }
        throw invalid();
    }
    private void space() {
        while (position < text.length() && " \t\r\n".indexOf(text.charAt(position)) >= 0) position++;
    }
    private char peek() { return position >= text.length() ? '\0' : text.charAt(position); }
    private boolean take(char value) { if (peek() != value) return false; position++; return true; }
    private void require(char value) { if (!take(value)) throw invalid(); }
    private static boolean digit(char value) { return value >= '0' && value <= '9'; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Malformed legacy presentation JSON."); }
}
