package com.socialmonitor.bilibili.live.session.export;

import java.util.List;

final class BilibiliLiveSessionExportColumns {

    static final ColumnSet SUMMARY = columns(
            List.of(
                    "id", "monitor_id", "uid", "room_id", "state", "started_at", "ended_at",
                    "start_source", "end_source", "coverage_status", "transport_session_count",
                    "capture_started_at", "capture_ended_at", "danmaku_count", "gift_event_count", "gift_count",
                    "free_gift_count", "gift_sender_count", "paid_user_count", "interacting_user_count",
                    "unresolved_interacting_event_count", "unresolved_gift_event_count",
                    "unresolved_paid_event_count", "paid_event_count", "paid_amount_milli_yuan",
                    "first_event_at", "last_event_at"),
            List.of(
                    "场次记录 ID", "直播监控记录 ID", "主播 B站 UID", "直播间 ID", "场次状态", "场次开始时间",
                    "场次结束时间", "开始边界来源", "结束边界来源", "数据覆盖状态", "WebSocket 传输会话数",
                    "在线采集开始时间", "在线采集结束时间", "弹幕事件数", "礼物事件数", "礼物数量",
                    "免费礼物数量", "已识别送礼用户数", "已识别付费用户数", "已识别互动用户数",
                    "未解析身份的互动事件数", "未解析身份的送礼事件数", "未解析身份的付费事件数",
                    "付费事件数", "消费金额（千分之一元）", "首条事件时间", "末条事件时间"));

    static final ColumnSet DANMAKU = columns(
            List.of(
                    "occurred_at", "received_at", "sender_uid", "sender_name", "medal_name", "message_text",
                    "command", "protocol_version", "source_event_id"),
            List.of(
                    "事件发生时间", "系统接收时间", "发送者 B站 UID", "发送者昵称", "粉丝勋章名称", "弹幕内容",
                    "B站消息命令", "协议版本", "上游事件 ID"));

    static final ColumnSet GIFTS = columns(
            List.of(
                    "occurred_at", "received_at", "event_kind", "sender_uid", "sender_name", "medal_name",
                    "message_text", "gift_id", "gift_name", "gift_count", "coin_type",
                    "unit_price_milli_yuan", "paid_amount_milli_yuan", "paid", "guard_level", "amount_source",
                    "command", "protocol_version", "source_event_id", "event_key", "transport_session_id"),
            List.of(
                    "事件发生时间", "系统接收时间", "事件类型", "发送者 B站 UID", "发送者昵称", "粉丝勋章名称",
                    "消息文本", "礼物 ID", "礼物名称", "礼物数量", "平台币类型", "单件价格（千分之一元）",
                    "实付金额（千分之一元）", "是否付费", "舰队等级", "金额来源", "B站消息命令", "协议版本",
                    "上游事件 ID", "事件去重键", "WebSocket 传输会话 ID"));

    static final ColumnSet USERS = columns(
            List.of(
                    "actor_key", "identity_quality", "user_uid", "display_name", "danmaku_count",
                    "gift_event_count", "gift_count", "free_gift_count", "paid_event_count",
                    "paid_amount_milli_yuan", "first_seen_at", "last_seen_at"),
            List.of(
                    "身份聚合键", "身份识别质量", "用户 B站 UID", "用户昵称", "弹幕事件数", "礼物事件数",
                    "礼物数量", "免费礼物数量", "付费事件数", "消费金额（千分之一元）", "首次出现时间",
                    "最后出现时间"));

    private BilibiliLiveSessionExportColumns() {
    }

    private static ColumnSet columns(List<String> headers, List<String> comments) {
        return new ColumnSet(headers, comments);
    }

    record ColumnSet(List<String> headers, List<String> comments) {

        ColumnSet {
            headers = List.copyOf(headers);
            comments = List.copyOf(comments);
            if (headers.size() != comments.size()) {
                throw new IllegalArgumentException("Export headers and comments must have the same size.");
            }
        }
    }
}
