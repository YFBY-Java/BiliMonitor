package com.socialmonitor.bilibili.live.session.export;

import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import java.util.Arrays;

public enum BilibiliLiveSessionExportCategory {
    DANMAKU("danmaku", "csv"),
    GIFTS("gifts", "csv"),
    USERS("users", "csv"),
    ALL("all", "zip");

    private final String wireValue;
    private final String extension;

    BilibiliLiveSessionExportCategory(String wireValue, String extension) {
        this.wireValue = wireValue;
        this.extension = extension;
    }

    public String wireValue() {
        return wireValue;
    }

    public String extension() {
        return extension;
    }

    public static BilibiliLiveSessionExportCategory parse(String value) {
        return Arrays.stream(values())
                .filter(category -> category.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "category must be one of: danmaku, gifts, users, all"
                ));
    }
}
