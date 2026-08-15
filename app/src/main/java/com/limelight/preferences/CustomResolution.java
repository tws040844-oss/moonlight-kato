package com.limelight.preferences;

final class CustomResolution {
    static final String PREFIX = "custom:";

    private static final int MIN_DIMENSION = 256;
    private static final int MAX_DIMENSION = 8192;
    private static final long MAX_PIXELS = 7680L * 4320L;

    enum ValidationError {
        NONE,
        EMPTY,
        FORMAT,
        OUT_OF_RANGE,
        ODD_DIMENSION,
        TOO_MANY_PIXELS
    }

    static final class ValidationResult {
        final ValidationError error;
        final int width;
        final int height;

        private ValidationResult(ValidationError error, int width, int height) {
            this.error = error;
            this.width = width;
            this.height = height;
        }

        boolean isValid() {
            return error == ValidationError.NONE;
        }

        String rawValue() {
            return isValid() ? formatRaw(width, height) : null;
        }

        String encodedValue() {
            return isValid() ? encode(width, height) : null;
        }
    }

    private CustomResolution() {
    }

    static ValidationResult validate(String widthText, String heightText) {
        if (widthText == null || heightText == null) {
            return invalid(ValidationError.EMPTY);
        }

        String normalizedWidth = widthText.trim();
        String normalizedHeight = heightText.trim();
        if (normalizedWidth.isEmpty() || normalizedHeight.isEmpty()) {
            return invalid(ValidationError.EMPTY);
        }
        if (!isDecimal(normalizedWidth) || !isDecimal(normalizedHeight)) {
            return invalid(ValidationError.FORMAT);
        }

        long width;
        long height;
        try {
            width = Long.parseLong(normalizedWidth);
            height = Long.parseLong(normalizedHeight);
        }
        catch (NumberFormatException e) {
            return invalid(ValidationError.OUT_OF_RANGE);
        }

        if (width < MIN_DIMENSION || width > MAX_DIMENSION ||
                height < MIN_DIMENSION || height > MAX_DIMENSION) {
            return invalid(ValidationError.OUT_OF_RANGE, width, height);
        }

        if ((width & 1L) != 0 || (height & 1L) != 0) {
            return invalid(ValidationError.ODD_DIMENSION, width, height);
        }

        if (width * height > MAX_PIXELS) {
            return invalid(ValidationError.TOO_MANY_PIXELS, width, height);
        }

        return new ValidationResult(ValidationError.NONE, (int) width, (int) height);
    }

    static ValidationResult parseRaw(String raw) {
        if (raw == null) {
            return invalid(ValidationError.EMPTY);
        }

        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return invalid(ValidationError.EMPTY);
        }

        int separator = normalized.indexOf('x');
        if (separator < 0 || separator != normalized.lastIndexOf('x')) {
            return invalid(ValidationError.FORMAT);
        }

        String widthText = normalized.substring(0, separator);
        String heightText = normalized.substring(separator + 1);
        if (widthText.isEmpty() || heightText.isEmpty()) {
            return invalid(ValidationError.EMPTY);
        }

        // Only outer whitespace is accepted. Whitespace around the separator is a format error.
        if (!widthText.equals(widthText.trim()) || !heightText.equals(heightText.trim())) {
            return invalid(ValidationError.FORMAT);
        }

        return validate(widthText, heightText);
    }

    static ValidationResult parseEncoded(String encoded) {
        if (encoded == null) {
            return invalid(ValidationError.EMPTY);
        }

        String normalized = encoded.trim();
        if (normalized.isEmpty()) {
            return invalid(ValidationError.EMPTY);
        }
        if (!isEncoded(normalized)) {
            return invalid(ValidationError.FORMAT);
        }

        String raw = normalized.substring(PREFIX.length());
        if (raw.isEmpty()) {
            return invalid(ValidationError.EMPTY);
        }
        if (!raw.equals(raw.trim())) {
            return invalid(ValidationError.FORMAT);
        }

        return parseRaw(raw);
    }

    static boolean isEncoded(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    static String encode(int width, int height) {
        return PREFIX + formatRaw(width, height);
    }

    static String formatRaw(int width, int height) {
        return width + "x" + height;
    }

    private static boolean isDecimal(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static ValidationResult invalid(ValidationError error) {
        return new ValidationResult(error, 0, 0);
    }

    private static ValidationResult invalid(ValidationError error, long width, long height) {
        int safeWidth = width <= Integer.MAX_VALUE ? (int) width : 0;
        int safeHeight = height <= Integer.MAX_VALUE ? (int) height : 0;
        return new ValidationResult(error, safeWidth, safeHeight);
    }
}
