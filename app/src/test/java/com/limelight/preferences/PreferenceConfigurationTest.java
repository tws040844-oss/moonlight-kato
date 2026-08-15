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
}
