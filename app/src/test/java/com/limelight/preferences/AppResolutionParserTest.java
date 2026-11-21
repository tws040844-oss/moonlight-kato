package com.limelight.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppResolutionParserTest {
    private static PreferenceConfiguration baseConfig(int width, int height, int fps) {
        PreferenceConfiguration config = new PreferenceConfiguration();
        config.width = width;
        config.height = height;
        config.fps = fps;
        config.bitrate = PreferenceConfiguration.getDefaultBitrate(width + "x" + height, String.valueOf(fps));
        config.autoAppResolution = true;
        return config;
    }

    @Test
    public void extractsPortraitAndLandscapeFold8UltraResolutions() {
        CustomResolution.ValidationResult portrait =
                AppResolutionParser.extractFromAppName("Desktop｜Fold8U 展開直向 (效率 1968×2184)");
        assertTrue(portrait.isValid());
        assertEquals(1968, portrait.width);
        assertEquals(2184, portrait.height);

        CustomResolution.ValidationResult perf169 =
                AppResolutionParser.extractFromAppName("Steam｜Fold8U 16:9 (效能 2496×1404)");
        assertTrue(perf169.isValid());
        assertEquals(2496, perf169.width);
        assertEquals(1404, perf169.height);

        CustomResolution.ValidationResult asciiLower =
                AppResolutionParser.extractFromAppName("Desktop Fold8U 2520x1080");
        assertTrue(asciiLower.isValid());
        assertEquals(2520, asciiLower.width);
        assertEquals(1080, asciiLower.height);

        CustomResolution.ValidationResult asterisk =
                AppResolutionParser.extractFromAppName("Fold8 Full 2504*2256");
        assertTrue(asterisk.isValid());
        assertEquals(2504, asterisk.width);
        assertEquals(2256, asterisk.height);
    }

    @Test
    public void ignoresNonResolutionTokensAndInvalidDimensions() {
        assertFalse(AppResolutionParser.extractFromAppName("Desktop｜原生雙螢幕").isValid());
        assertFalse(AppResolutionParser.extractFromAppName("Steam Big Picture 16:9 2K 4K").isValid());
        assertFalse(AppResolutionParser.extractFromAppName("App 1921×1080").isValid()); // odd width
        assertFalse(AppResolutionParser.extractFromAppName("App 200×200").isValid()); // below 256
        assertFalse(AppResolutionParser.extractFromAppName("App 8192×8192").isValid()); // over 8K pixel cap
        assertFalse(AppResolutionParser.extractFromAppName(null).isValid());
    }

    @Test
    public void picksLastValidMatchWhenEarlierInvalidPairAppears() {
        CustomResolution.ValidationResult res =
                AppResolutionParser.extractFromAppName("Mode 100x200 -> 1968×2184");
        assertTrue(res.isValid());
        assertEquals(1968, res.width);
        assertEquals(2184, res.height);
    }

    @Test
    public void appliesResolutionAndScalesDefaultBitrateOnly() {
        PreferenceConfiguration defaultBitrateConfig = baseConfig(1280, 720, 60);
        boolean applied = AppResolutionParser.applyAppNameResolution(
                defaultBitrateConfig, "Desktop｜Fold8U 展開直向 (效率 1968×2184)", false);
        assertTrue(applied);
        assertEquals(1968, defaultBitrateConfig.width);
        assertEquals(2184, defaultBitrateConfig.height);
        assertTrue(defaultBitrateConfig.customResolution);
        assertTrue(defaultBitrateConfig.autoAppliedAppResolution);
        assertFalse(defaultBitrateConfig.isNativeResolution());
        assertEquals(
                PreferenceConfiguration.getDefaultBitrate("1968x2184", "60"),
                defaultBitrateConfig.bitrate);

        // User-customized bitrate must be preserved
        PreferenceConfiguration customBitrateConfig = baseConfig(1280, 720, 60);
        customBitrateConfig.bitrate = 88000;
        assertTrue(AppResolutionParser.applyAppNameResolution(
                customBitrateConfig, "Steam｜Fold8U 16:9 (效能 2496×1404)", false));
        assertEquals(2496, customBitrateConfig.width);
        assertEquals(1404, customBitrateConfig.height);
        assertEquals(88000, customBitrateConfig.bitrate);
    }

    @Test
    public void respectsRootBuildAndDisabledSetting() {
        PreferenceConfiguration rootConfig = baseConfig(1280, 720, 60);
        assertFalse(AppResolutionParser.applyAppNameResolution(
                rootConfig, "Desktop｜Fold8U 展開直向 (效率 1968×2184)", true));
        assertEquals(1280, rootConfig.width);
        assertFalse(rootConfig.autoAppliedAppResolution);

        PreferenceConfiguration disabledConfig = baseConfig(1280, 720, 60);
        disabledConfig.autoAppResolution = false;
        assertFalse(AppResolutionParser.applyAppNameResolution(
                disabledConfig, "Desktop｜Fold8U 展開直向 (效率 1968×2184)", false));
        assertEquals(1280, disabledConfig.width);
        assertFalse(disabledConfig.autoAppliedAppResolution);
    }
}
