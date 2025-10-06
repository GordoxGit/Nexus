package com.heneria.nexus.listener.region;

import com.heneria.nexus.api.region.RegionFlag;
import java.util.Map;

final class RegionFlagUtil {

    private RegionFlagUtil() {
    }

    static boolean getBoolean(Map<RegionFlag, Object> flags, RegionFlag flag, boolean defaultValue) {
        Object value = flags.get(flag);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String string) {
            if (string.equalsIgnoreCase("true") || string.equalsIgnoreCase("yes") || string.equalsIgnoreCase("on")) {
                return true;
            }
            if (string.equalsIgnoreCase("false") || string.equalsIgnoreCase("no") || string.equalsIgnoreCase("off")) {
                return false;
            }
        }
        return defaultValue;
    }

    static double getDouble(Map<RegionFlag, Object> flags, RegionFlag flag, double defaultValue) {
        Object value = flags.get(flag);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
