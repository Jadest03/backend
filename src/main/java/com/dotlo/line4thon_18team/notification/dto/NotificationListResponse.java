package com.dotlo.line4thon_18team.notification.dto;

import java.util.List;

/** @param notifications 최근 50개, 최신순 */
public record NotificationListResponse(List<NotificationResponse> notifications) {
}
