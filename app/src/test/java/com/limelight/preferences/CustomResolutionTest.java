package com.limelight.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CustomResolutionTest {
    private static void assertInvalid(CustomResolution.ValidationResult result,
                                      CustomResolution.ValidationError expectedError) {
        assertFalse(result.isValid());
        assertEquals(expectedError, result.error);
        assertNull(result.rawValue());
        assertNull(result.encodedValue());
    }

    @Test
    public void validateProducesCanonicalRawAndEncodedValues() {
        CustomResolution.ValidationResult result = CustomResolution.validate("2184", "1228");

        assertTrue(result.isValid());
        assertEquals(CustomResolution.ValidationError.NONE, result.error);
        assertEquals(2184, result.width);
        assertEquals(1228, result.height);
        assertEquals("2184x1228", result.rawValue());
        assertEquals("custom:2184x1228", result.encodedValue());
    }

    @Test
    public void validateTrimsFieldsAndRemovesLeadingZeroes() {
        CustomResolution.ValidationResult result = CustomResolution.validate(" 02184 ", " 01228 ");

        assertTrue(result.isValid());
        assertEquals(2184, result.width);
        assertEquals(1228, result.height);
        assertEquals("2184x1228", result.rawValue());
    }

    @Test
    public void parseRawAndEncodedRoundTripCanonicalValues() {
        CustomResolution.ValidationResult raw = CustomResolution.parseRaw(" 2496x1404 ");
        CustomResolution.ValidationResult encoded =
                CustomResolution.parseEncoded(" custom:2496x1404 ");

        assertTrue(raw.isValid());
        assertTrue(encoded.isValid());
        assertEquals(2496, raw.width);
        assertEquals(1404, raw.height);
        assertEquals(raw.rawValue(), encoded.rawValue());
        assertEquals("custom:2496x1404", raw.encodedValue());
    }

    @Test
    public void encodeAndFormatRawUseStableStorageSyntax() {
        assertEquals("2504x2256", CustomResolution.formatRaw(2504, 2256));
        assertEquals("custom:2504x2256", CustomResolution.encode(2504, 2256));
        assertTrue(CustomResolution.isEncoded("custom:2504x2256"));
        assertFalse(CustomResolution.isEncoded("2504x2256"));
    }

    @Test
    public void landscapePortraitAndSquareDimensionsAreAccepted() {
        CustomResolution.ValidationResult landscape =
                CustomResolution.validate("2520", "1080");
        CustomResolution.ValidationResult portrait =
                CustomResolution.validate("1080", "2520");
        CustomResolution.ValidationResult square =
                CustomResolution.validate("4096", "4096");

        assertTrue(landscape.isValid());
        assertTrue(portrait.isValid());
        assertTrue(square.isValid());
    }

    @Test
    public void inclusiveDimensionAndPixelBoundariesAreAccepted() {
        assertTrue(CustomResolution.validate("256", "256").isValid());
        assertTrue(CustomResolution.validate("7680", "4320").isValid());

        // 8192 * 4050 is exactly the same pixel count as 7680 * 4320.
        CustomResolution.ValidationResult maxWidthAtPixelLimit =
                CustomResolution.validate("8192", "4050");
        assertTrue(maxWidthAtPixelLimit.isValid());
        assertEquals("8192x4050", maxWidthAtPixelLimit.rawValue());
    }

    @Test
    public void nullEmptyAndWhitespaceOnlyFieldsAreRejected() {
        assertInvalid(CustomResolution.validate(null, "1080"),
                CustomResolution.ValidationError.EMPTY);
        assertInvalid(CustomResolution.validate("1920", null),
                CustomResolution.ValidationError.EMPTY);
        assertInvalid(CustomResolution.validate("", "1080"),
                CustomResolution.ValidationError.EMPTY);
        assertInvalid(CustomResolution.validate("1920", "   "),
                CustomResolution.ValidationError.EMPTY);
        assertInvalid(CustomResolution.parseRaw(null),
                CustomResolution.ValidationError.EMPTY);
        assertInvalid(CustomResolution.parseEncoded(""),
                CustomResolution.ValidationError.EMPTY);
    }

    @Test
    public void malformedNumbersAndSeparatorsAreRejected() {
        assertInvalid(CustomResolution.validate("1920.0", "1080"),
                CustomResolution.ValidationError.FORMAT);
        assertInvalid(CustomResolution.validate("-1920", "1080"),
                CustomResolution.ValidationError.FORMAT);
        assertInvalid(CustomResolution.validate("+1920", "1080"),
                CustomResolution.ValidationError.FORMAT);
        assertInvalid(CustomResolution.validate("2e3", "1080"),
                CustomResolution.ValidationError.FORMAT);
        assertInvalid(CustomResolution.parseRaw("1920X1080"),
                CustomResolution.ValidationError.FORMAT);
        assertInvalid(CustomResolution.parseRaw("1920×1080"),
                CustomResolution.ValidationError.FORMAT);
        assertInvalid(CustomResolution.parseRaw("1920x1080x60"),
                CustomResolution.ValidationError.FORMAT);
        assertInvalid(CustomResolution.parseEncoded("CUSTOM:1920x1080"),
                CustomResolution.ValidationError.FORMAT);
        assertInvalid(CustomResolution.parseEncoded("custom: 1920x1080"),
                CustomResolution.ValidationError.FORMAT);
        assertInvalid(CustomResolution.parseEncoded("custom:1920 x1080"),
                CustomResolution.ValidationError.FORMAT);
        assertInvalid(CustomResolution.parseEncoded("1920x1080"),
                CustomResolution.ValidationError.FORMAT);
        assertInvalid(CustomResolution.parseRaw("custom:1920x1080"),
                CustomResolution.ValidationError.FORMAT);
    }

    @Test
    public void integerOverflowIsRejectedWithoutThrowing() {
        assertInvalid(CustomResolution.validate("999999999999999999999999", "1080"),
                CustomResolution.ValidationError.OUT_OF_RANGE);
        assertInvalid(CustomResolution.parseEncoded(
                        "custom:999999999999999999999999x1080"),
                CustomResolution.ValidationError.OUT_OF_RANGE);
    }

    @Test
    public void dimensionsOutsideInclusiveRangeAreRejectedBeforeParity() {
        assertInvalid(CustomResolution.validate("254", "256"),
                CustomResolution.ValidationError.OUT_OF_RANGE);
        assertInvalid(CustomResolution.validate("256", "254"),
                CustomResolution.ValidationError.OUT_OF_RANGE);
        assertInvalid(CustomResolution.validate("8194", "256"),
                CustomResolution.ValidationError.OUT_OF_RANGE);
        assertInvalid(CustomResolution.validate("256", "8194"),
                CustomResolution.ValidationError.OUT_OF_RANGE);
        assertInvalid(CustomResolution.validate("255", "255"),
                CustomResolution.ValidationError.OUT_OF_RANGE);
        assertInvalid(CustomResolution.validate("8193", "8193"),
                CustomResolution.ValidationError.OUT_OF_RANGE);
    }

    @Test
    public void oddDimensionsWithinRangeAreRejected() {
        assertInvalid(CustomResolution.validate("2185", "1228"),
                CustomResolution.ValidationError.ODD_DIMENSION);
        assertInvalid(CustomResolution.validate("2184", "1229"),
                CustomResolution.ValidationError.ODD_DIMENSION);
    }

    @Test
    public void dimensionsAboveEightKUhdPixelCountAreRejected() {
        assertInvalid(CustomResolution.validate("8192", "4052"),
                CustomResolution.ValidationError.TOO_MANY_PIXELS);
        assertInvalid(CustomResolution.validate("8192", "8192"),
                CustomResolution.ValidationError.TOO_MANY_PIXELS);
    }
}
