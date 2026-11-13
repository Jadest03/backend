package com.dotlo.line4thon_18team.chat;

import com.dotlo.line4thon_18team.chat.dto.ChatMessageListResponse;
import com.dotlo.line4thon_18team.chat.dto.ChatMessageResponse;
import com.dotlo.line4thon_18team.chat.entity.ChatMessage;
import com.dotlo.line4thon_18team.chat.entity.ChatMessageType;
import com.dotlo.line4thon_18team.chat.repository.ChatMessageRepository;
import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.trip.service.TripService;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여행 채팅방 메시지. 실시간은 폴링이다: FE 가 2~3초마다 마지막으로 받은 id 다음을 가져간다.
 * 전송 API 는 사람 메시지(USER)만 넣고, SYSTEM·AI 는 서버 안에서 appendSystem·appendAi 로 넣는다
 * (FE 가 도틀로인 척 메시지를 넣지 못하게).
 *
 * <p>저장할 때 여행 행을 잠근다. 같은 여행 안에서는 앞 메시지가 커밋돼야 다음 메시지가 id 를 받아서
 * id 순서와 커밋 순서가 같아진다. 그래서 122번이 보이면 121번은 이미 보이고, after 커서로 폴링해도
 * 늦게 커밋된 메시지를 건너뛰지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private final TripService tripService;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;

    // 구성원이 아니면 404
    @Transactional
    public ChatMessageResponse send(Long userId, Long tripId, String text) {
        Trip trip = lockTrip(tripId);
        if (!tripService.isMember(trip, userId)) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        ChatMessage message = ChatMessage.create(trip, userRepository.getReferenceById(userId),
                ChatMessageType.USER, text, null);
        return ChatMessageResponse.from(chatMessageRepository.save(message));
    }

    /**
     * after 다음 메시지를 오래된 것부터 최대 100개. after 가 없으면 처음부터.
     * 100개가 꽉 차서 오면 마지막 id 로 바로 한 번 더 부르면 된다. 구성원이 아니면 404.
     */
    public ChatMessageListResponse list(Long userId, Long tripId, Long after) {
        tripService.findMemberTrip(userId, tripId);
        return new ChatMessageListResponse(chatMessageRepository
                .findTop100ByTripIdAndIdGreaterThanOrderByIdAsc(tripId, after == null ? 0L : after)
                .stream().map(ChatMessageResponse::from).toList());
    }

    /** "○○님이 여행에 참여했어요" 같은 알림. 권한 확인은 부르는 쪽이 한다. */
    @Transactional
    public ChatMessageResponse appendSystem(long tripId, String text) {
        return append(tripId, ChatMessageType.SYSTEM, text, null);
    }

    /**
     * 도틀로 응답. 권한 확인은 부르는 쪽이 한다.
     *
     * @param payloadJson 카드·선택지 JSON. 열어보지 않고 그대로 저장해서 돌려준다. 없으면 null
     */
    @Transactional
    public ChatMessageResponse appendAi(long tripId, String text, String payloadJson) {
        return append(tripId, ChatMessageType.AI, text, payloadJson);
    }

    private ChatMessageResponse append(long tripId, ChatMessageType type, String text, String payloadJson) {
        ChatMessage message = ChatMessage.create(lockTrip(tripId), null, type, text, payloadJson);
        return ChatMessageResponse.from(chatMessageRepository.save(message));
    }

    // ponytail: 여행 행 잠금이라 같은 여행의 좋아요·댓글 쓰기와도 줄을 선다. 일행 몇 명 규모라 충분하다
    private Trip lockTrip(long tripId) {
        return tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
    }
}
