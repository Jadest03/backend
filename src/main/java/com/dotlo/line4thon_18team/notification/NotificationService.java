package com.dotlo.line4thon_18team.notification;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.notification.dto.NotificationListResponse;
import com.dotlo.line4thon_18team.notification.dto.NotificationResponse;
import com.dotlo.line4thon_18team.notification.dto.UnreadResponse;
import com.dotlo.line4thon_18team.notification.entity.Notification;
import com.dotlo.line4thon_18team.notification.entity.NotificationType;
import com.dotlo.line4thon_18team.notification.repository.NotificationRepository;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 홈 알림. 받는 사람마다 한 줄씩 저장하고, 내 알림 최근 50개를 보여준다.
 * 다른 기능은 notifyTripMembers·notifyUser 를 불러 알림을 만든다 (권한 확인은 부르는 쪽이 한다).
 * 여행에서 나가면 그 여행 알림은 지운다 (눌러도 이제 볼 수 없는 여행이라).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;

    public NotificationListResponse list(Long userId) {
        return new NotificationListResponse(notificationRepository.findTop50ByUserIdOrderByIdDesc(userId).stream()
                .map(NotificationResponse::from).toList());
    }

    public UnreadResponse unread(Long userId) {
        return new UnreadResponse(notificationRepository.existsByUserIdAndReadAtIsNull(userId));
    }

    // 알림 하나를 눌렀을 때. 내 알림이 아니면 404
    @Transactional
    public void read(Long userId, Long notificationId) {
        notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND))
                .read(LocalDateTime.now());
    }

    @Transactional
    public void readAll(Long userId) {
        notificationRepository.readAll(userId, LocalDateTime.now());
    }

    /**
     * 여행 일행(방장 + 멤버) 모두에게 알린다. 행동한 사람은 빼고.
     *
     * @param actorId   행동한 사람. AI 초안 완성처럼 사람이 없으면 null (모두 받음)
     * @param highlight message 안에서 굵게 보일 부분 (장소 이름 등). 없으면 null
     */
    @Transactional
    public void notifyTripMembers(long tripId, Long actorId, NotificationType type, String message, String highlight) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        List<User> recipients = new ArrayList<>();
        recipients.add(trip.getUser());
        tripMemberRepository.findAllByTripIdOrderByIdAsc(tripId).forEach(member -> recipients.add(member.getUser()));
        notificationRepository.saveAll(recipients.stream()
                .filter(user -> !user.getId().equals(actorId))
                .map(user -> Notification.create(user, trip, type, message, highlight))
                .toList());
    }

    /** 한 사람에게만 알린다 (댓글 멘션). 받는 사람이 그 여행 일행인지는 부르는 쪽이 본다. */
    @Transactional
    public void notifyUser(long tripId, User recipient, NotificationType type, String message, String highlight) {
        notificationRepository.save(Notification.create(recipient, tripRepository.getReferenceById(tripId),
                type, message, highlight));
    }
}
