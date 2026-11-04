package com.phase.api.common;

import java.util.Locale;

public final class RutValidator {

    private RutValidator() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.replace(".", "")
                .replace(" ", "")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    public static boolean isValidRutFormat(String value) {
        String rut = normalize(value);
        return rut != null && rut.matches("^[0-9]{7,9}-[0-9K]$");
    }

    public static boolean isValidRut(String value) {
        String rut = normalize(value);
        if (rut == null || !rut.matches("^[0-9]{7,9}-[0-9K]$")) {
            return false;
        }

        String[] parts = rut.split("-");
        String body = parts[0];
        String dv = parts[1];

        int sum = 0;
        int multiplier = 2;
        for (int i = body.length() - 1; i >= 0; i--) {
            sum += Character.getNumericValue(body.charAt(i)) * multiplier;
            multiplier = multiplier == 7 ? 2 : multiplier + 1;
        }

        int remainder = 11 - (sum % 11);
        String expected = remainder == 11 ? "0" : remainder == 10 ? "K" : String.valueOf(remainder);
        return expected.equals(dv);
    }

    public static boolean isValidForeignDocument(String value) {
        if (value == null) {
            return false;
        }
        return normalize(value).matches("^[A-Z0-9-]{3,20}$");
    }
}
