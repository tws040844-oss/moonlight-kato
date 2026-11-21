package com.limelight.preferences;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AppResolutionParser {
    private static final Pattern RESOLUTION_PATTERN =
            Pattern.compile("(?<!\\d)(\\d{3,4})\\s*[xX×＊*]\\s*(\\d{3,4})(?!\\d)");

    private AppResolutionParser() {
    }

    static CustomResolution.ValidationResult extractFromAppName(String appName) {
        if (appName == null || appName.trim().isEmpty()) {
            return CustomResolution.parseRaw("");
        }

        Matcher matcher = RESOLUTION_PATTERN.matcher(appName);
        CustomResolution.ValidationResult lastValid = null;
        while (matcher.find()) {
            CustomResolution.ValidationResult candidate =
                    CustomResolution.validate(matcher.group(1), matcher.group(2));
            if (candidate.isValid()) {
                lastValid = candidate;
            }
        }

        return lastValid != null ? lastValid : CustomResolution.parseRaw("");
    }

    public static boolean applyAppNameResolution(
            PreferenceConfiguration config, String appName, boolean rootBuild) {
        if (rootBuild || config == null || !config.autoAppResolution) {
            return false;
        }

        CustomResolution.ValidationResult result = extractFromAppName(appName);
        if (!result.isValid()) {
            return false;
        }

        int effectiveFps = config.fps > 0 ? config.fps : Integer.parseInt(PreferenceConfiguration.DEFAULT_FPS);
        String fpsString = String.valueOf(effectiveFps);
        String oldResString = config.width + "x" + config.height;
        int oldDefaultBitrate = PreferenceConfiguration.getDefaultBitrate(oldResString, fpsString);

        config.width = result.width;
        config.height = result.height;
        config.customResolution = true;
        config.autoAppliedAppResolution = true;

        if (config.bitrate == oldDefaultBitrate) {
            config.bitrate = PreferenceConfiguration.getDefaultBitrate(result.rawValue(), fpsString);
        }

        return true;
    }
}
