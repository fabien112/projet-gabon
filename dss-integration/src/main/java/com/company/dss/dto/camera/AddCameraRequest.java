package com.company.dss.dto.camera;

import jakarta.validation.constraints.NotBlank;

public record AddCameraRequest(
        @NotBlank(message = "channelId est obligatoire")
        String channelId
) {
}
