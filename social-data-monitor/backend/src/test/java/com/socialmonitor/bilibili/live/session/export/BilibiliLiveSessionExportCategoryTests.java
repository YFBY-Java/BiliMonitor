package com.socialmonitor.bilibili.live.session.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BilibiliLiveSessionExportCategoryTests {

    @ParameterizedTest
    @CsvSource({
            "danmaku,DANMAKU",
            "gifts,GIFTS",
            "users,USERS",
            "all,ALL"
    })
    void acceptsOnlyTheStableLowercaseWireValues(String wireValue, String expected) {
        assertThat(BilibiliLiveSessionExportCategory.parse(wireValue).name()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "gift", "summary", "ALL", "../all", "danmaku.csv"})
    void rejectsUnknownOrUnsafeValues(String value) {
        assertThatThrownBy(() -> BilibiliLiveSessionExportCategory.parse(value))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }
}
