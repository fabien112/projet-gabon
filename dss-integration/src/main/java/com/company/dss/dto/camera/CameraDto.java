package com.company.dss.dto.camera;

public record CameraDto(
        Long id,
        String channelId,
        String name,
        String site,
        boolean active
) {
}
