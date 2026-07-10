package com.socialmonitor.collector.dto;

import jakarta.validation.constraints.NotBlank;

public record CollectTaskCommand(
        @NotBlank String platformCode,
        @NotBlank String dataType,
        String externalId,
        String cursor
) {
}

