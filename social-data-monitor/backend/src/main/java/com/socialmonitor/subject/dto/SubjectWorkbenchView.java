package com.socialmonitor.subject.dto;

import java.util.List;

public record SubjectWorkbenchView(
        SubjectView subject,
        SubjectBilibiliBindingView bilibiliBinding,
        SubjectBilibiliUserView bilibiliUser,
        SubjectBilibiliLiveRoomView bilibiliLiveRoom,
        SubjectSummaryView summary,
        SubjectDanmuView danmu,
        List<SubjectWidgetLayoutView> layout,
        List<SubjectHealthEventView> recentEvents
) {
}
