package com.company.dss.passengerflow;

/**
 * Règles communes pour identifier une caméra compteuse « principale »
 * (canal vidéo {@code $1$}, hors doublons techniques {@code $3$} / {@code *_1}).
 */
public final class CompteuseCameraRules {

    private CompteuseCameraRules() {
    }

    public static boolean isPrimary(String channelId, String name, boolean active) {
        if (!isConfigured(channelId, active)) {
            return false;
        }
        String safeName = name == null ? "" : name;
        if (safeName.matches("(?i).*_1\\s*$")) {
            return false;
        }
        return PassengerFlowClient.isCompteuseVideoChannel(safeName, channelId);
    }

    /** Caméra active avec un canal vidéo principal DSS ({@code $1$}). */
    public static boolean isConfigured(String channelId, boolean active) {
        return active && PassengerFlowClient.isMainVideoChannelCode(channelId);
    }
}
