package com.dotlo.line4thon_18team.notification.dto;

import com.dotlo.line4thon_18team.notification.entity.Notification;
import com.dotlo.line4thon_18team.notification.entity.NotificationType;
import com.dotlo.line4thon_18team.trip.entity.Trip;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * @param tripTitle 알림 위 작은 제목 ("도쿄 3박 4일"). 종류 꼬리표(· 댓글)는 FE 가 type 으로 붙인다
 * @param highlight message 안에서 굵게 보일 부분. 없으면 null
 */
public record NotificationResponse(Long id, NotificationType type, Long tripId, String tripTitle,
                                   String message, String highlight, boolean read, LocalDateTime createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTrip().getId(), title(n.getTrip()),
                n.getMessage(), n.getHighlight(), n.getReadAt() != null, n.getCreatedAt());
    }

    static String title(Trip trip) {
        long nights = ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate());
        return nights == 0 ? trip.getCity() + " 당일치기" : trip.getCity() + " " + nights + "박 " + (nights + 1) + "일";
    }
}
