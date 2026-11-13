package com.dotlo.line4thon_18team.chat;

import com.dotlo.line4thon_18team.chat.dto.ChatMessageListResponse;
import com.dotlo.line4thon_18team.chat.dto.ChatMessageResponse;
import com.dotlo.line4thon_18team.chat.entity.ChatMessage;
import com.dotlo.line4thon_18team.chat.entity.ChatMessageType;
import com.dotlo.line4thon_18team.chat.repository.ChatMessageRepository;
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
        Trip trip = tripService.findMemberTrip(userId, tripId);
        ChatMessage message = ChatMessage.create(trip, userRepository.getReferenceById(userId),
                ChatMessageType.USER, text, null);
        return ChatMessageResponse.from(chatMessageRepository.save(message));
    }

    /**
     * after 다음 메시지를 오래된 것부터 최대 100개. after 가 없으면 처음부터.
     * 100개가 꽉 차서 오면 마지막 id 로 바로 한 번 더 부르면 된다. 구성원이 아니면 404.
     */
    // ponytail: id 순 폴링이라, 먼저 id 를 받은 메시지가 더 늦게 커밋되면 그 폴링에서 빠질 수 있다 (새로고침하면 보임). 동시 전송이 많아지면 몇 초 겹쳐 가져오기로 바꾼다
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
        ChatMessage message = ChatMessage.create(tripRepository.getReferenceById(tripId), null, type, text, payloadJson);
        return ChatMessageResponse.from(chatMessageRepository.save(message));
    }
}
