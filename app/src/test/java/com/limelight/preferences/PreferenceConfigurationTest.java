package com.limelight.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.limelight.BuildConfig;

import org.junit.Test;

public class PreferenceConfigurationTest {
    @Test
    public void katoFixedResolutionClassificationIsFlavorScoped() {
        if (BuildConfig.ROOT_BUILD) {
            assertTrue(PreferenceConfiguration.isNativeResolution(2184, 1228));
            assertTrue(PreferenceConfiguration.isNativeResolution(2496, 1404));
        }
        else {
            assertFalse(PreferenceConfiguration.isNativeResolution(2184, 1228));
            assertFalse(PreferenceConfiguration.isNativeResolution(2496, 1404));
        }
    }

    @Test
    public void fold8UltraPanelResolutionRemainsNative() {
        assertTrue(PreferenceConfiguration.isNativeResolution(2504, 2256));
    }

    @Test
    public void existingFixedResolutionOptionsRemainNonNative() {
        assertFalse(PreferenceConfiguration.isNativeResolution(640, 360));
        assertFalse(PreferenceConfiguration.isNativeResolution(854, 480));
        assertFalse(PreferenceConfiguration.isNativeResolution(1280, 720));
        assertFalse(PreferenceConfiguration.isNativeResolution(1920, 1080));
        assertFalse(PreferenceConfiguration.isNativeResolution(2560, 1440));
        assertFalse(PreferenceConfiguration.isNativeResolution(3840, 2160));
    }

    @Test
    public void customResolutionBitratesUseExistingInterpolation() {
        assertEquals(28000, PreferenceConfiguration.getDefaultBitrate(
                PreferenceConfiguration.RES_2184X1228, "60"));
        assertEquals(38000, PreferenceConfiguration.getDefaultBitrate(
                PreferenceConfiguration.RES_2496X1404, "60"));
    }

    @Test
    public void encodedCustomResolutionBitratesAreFlavorScoped() {
        if (BuildConfig.ROOT_BUILD) {
            // The root flavor must safely reject KATO's custom prefix and use 720p
            // while preserving the requested frame rate for bitrate calculation.
            assertEquals(10000, PreferenceConfiguration.getDefaultBitrate(
                    "custom:2184x1228", "60"));
            assertEquals(14000, PreferenceConfiguration.getDefaultBitrate(
                    "custom:7680x4320", "120"));
        }
        else {
            assertEquals(14000, PreferenceConfiguration.getDefaultBitrate(
                    "custom:2184x1228", "30"));
            assertEquals(28000, PreferenceConfiguration.getDefaultBitrate(
                    "custom:2184x1228", "60"));
            assertEquals(39000, PreferenceConfiguration.getDefaultBitrate(
                    "custom:2184x1228", "120"));
            assertEquals(38000, PreferenceConfiguration.getDefaultBitrate(
                    "custom:2496x1404", "60"));
            assertEquals(57000, PreferenceConfiguration.getDefaultBitrate(
                    "custom:2504x2256", "60"));
            assertEquals(28000, PreferenceConfiguration.getDefaultBitrate(
                    "custom:2520x1080", "60"));
            assertEquals(80000, PreferenceConfiguration.getDefaultBitrate(
                    "custom:7680x4320", "60"));
            assertEquals(113000, PreferenceConfiguration.getDefaultBitrate(
                    "custom:7680x4320", "120"));
        }
    }

    @Test
    public void malformedCustomResolutionBitrateFallsBackWithoutThrowing() {
        assertEquals(10000, PreferenceConfiguration.getDefaultBitrate(
                "custom:not-a-resolution", "60"));
        assertEquals(10000, PreferenceConfiguration.getDefaultBitrate(
                "custom:999999999999999999999999x1080", "60"));
        assertEquals(10000, PreferenceConfiguration.getDefaultBitrate(
                "custom:8192x8192", "60"));
    }

    @Test
    public void explicitlyCustomDimensionsNeverUseNativeDisplayBehavior() {
        PreferenceConfiguration config = new PreferenceConfiguration();
        config.width = 2504;
        config.height = 2256;

        config.customResolution = false;
        assertTrue(config.isNativeResolution());

        config.customResolution = true;
        assertFalse(config.isNativeResolution());

        config.width = 2520;
        config.height = 1080;
        assertFalse(config.isNativeResolution());
    }
}
