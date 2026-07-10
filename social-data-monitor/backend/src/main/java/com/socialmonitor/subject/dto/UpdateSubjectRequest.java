package com.socialmonitor.subject.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateSubjectRequest(
        @Size(max = 160) String displayName,
        String avatarUrl,
        String remark,
        List<String> tags,
        Boolean enabled
) {
}
