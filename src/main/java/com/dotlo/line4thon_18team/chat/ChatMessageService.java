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
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 여행 채팅방 메시지 보내기·가져오기. 실시간은 폴링이다: FE 가 몇 초마다 마지막으로 받은 id 다음을 가져간다.
 * 여행 구성원(방장 + 참여 멤버)만 쓸 수 있고, 아니면 404.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private final TripService tripService;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;

    // 사람이 쓴 메시지(TEXT)
    @Transactional
    public ChatMessageResponse send(Long userId, Long tripId, String text) {
        Trip trip = tripService.findMemberTrip(userId, tripId);
        ChatMessage message = ChatMessage.create(trip, userRepository.getReferenceById(userId),
                ChatMessageType.TEXT, text, null);
        return ChatMessageResponse.from(chatMessageRepository.save(message));
    }

    /**
     * after 가 없으면 최근 50개, 있으면 그 id 다음 메시지를 최대 100개. 둘 다 오래된 것부터.
     * 100개가 꽉 차서 오면 마지막 id 로 바로 한 번 더 부르면 된다.
     */
    // ponytail: id 순 폴링이라, 먼저 id 를 받은 메시지가 더 늦게 커밋되면 그 폴링에서 빠질 수 있다 (새로고침하면 보임). 동시 전송이 많아지면 몇 초 겹쳐 가져오기로 바꾼다
    public ChatMessageListResponse list(Long userId, Long tripId, Long after) {
        tripService.findMemberTrip(userId, tripId);
        List<ChatMessage> messages;
        if (after == null) {
            messages = new ArrayList<>(chatMessageRepository.findTop50ByTripIdOrderByIdDesc(tripId));
            Collections.reverse(messages);
        } else {
            messages = chatMessageRepository.findTop100ByTripIdAndIdGreaterThanOrderByIdAsc(tripId, after);
        }
        return new ChatMessageListResponse(messages.stream().map(ChatMessageResponse::from).toList());
    }

    /**
     * 서버 안에서 도틀로 답변(AI)이나 투표 카드(POLL)를 채팅방에 올릴 때 쓴다.
     * 권한 확인은 하지 않는다. 부르는 쪽이 이미 구성원인지 보고 들어온다.
     *
     * @param senderId AI 면 null, 투표면 만든 사람
     * @param payload  JSON 으로 저장할 객체 (AI: 의견 조율 응답, POLL: {"pollId": 1}). 없으면 null
     */
    @Transactional
    public ChatMessageResponse post(long tripId, Long senderId, ChatMessageType type, String text, Object payload) {
        ChatMessage message = ChatMessage.create(tripRepository.getReferenceById(tripId),
                senderId == null ? null : userRepository.getReferenceById(senderId),
                type, text, payload == null ? null : objectMapper.writeValueAsString(payload));
        return ChatMessageResponse.from(chatMessageRepository.save(message));
    }
}
