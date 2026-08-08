package com.company.dss.passengerflow;

import org.springframework.util.StringUtils;

/**
 * Règles communes pour identifier une caméra compteuse « principale »
 * (canal vidéo {@code $1$}, hors doublons techniques {@code $3$} / {@code *_1}).
 */
public final class CompteuseCameraRules {

    private CompteuseCameraRules() {
    }

    public static boolean isPrimary(String channelId, String name, boolean active) {
        if (!active || !StringUtils.hasText(channelId)) {
            return false;
        }
        String safeName = name == null ? "" : name;
        if (safeName.matches("(?i).*_1\\s*$")) {
            return false;
        }
        return PassengerFlowClient.isCompteuseVideoChannel(safeName, channelId);
    }
}
