package com.company.dss.dto.camera;

public record AvailableCameraDto(
        String channelId,
        String name,
        String deviceName,
        boolean alreadyAdded
) {
}
