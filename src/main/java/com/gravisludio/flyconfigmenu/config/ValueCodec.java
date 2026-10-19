package com.gravisludio.flyconfigmenu.config;

/** Text to value and back for the number fields, including Create Fly's {@code IntDisplay} annotation. */
public final class ValueCodec {
    private ValueCodec() {
    }

    public static String formatInt(int value, String intDisplay) {
        return switch (intDisplay == null ? "" : intDisplay) {
            case "#" -> "#" + Integer.toHexString(value).toUpperCase();
            case "0x" -> "0x" + Integer.toHexString(value).toUpperCase();
            case "0b" -> "0b" + Integer.toBinaryString(value);
            default -> Integer.toString(value);
        };
    }

    /** @return the integer, or {@code null} when the text is not one */
    public static Integer parseInt(String text, String intDisplay) {
        String s = text.strip();
        if (s.isEmpty()) {
            return null;
        }
        try {
            int radix = switch (intDisplay == null ? "" : intDisplay) {
                case "#", "0x" -> 16;
                case "0b" -> 2;
                default -> 10;
            };
            if (radix == 10) {
                return Integer.parseInt(s);
            }
            String lower = s.toLowerCase();
            String digits = lower.startsWith("#") ? s.substring(1) : lower.startsWith("0x") || lower.startsWith("0b") ? s.substring(2) : s;
            // Unsigned so that colors like 0xFF336699 (negative as an int) can be written.
            return Integer.parseUnsignedInt(digits, radix);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** @return the float, or {@code null} when the text is not a finite number */
    public static Float parseFloat(String text) {
        try {
            float value = Float.parseFloat(text.strip());
            return Float.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String formatFloat(float value) {
        return Float.toString(value);
    }
}
