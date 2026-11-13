package com.dotlo.line4thon_18team.chat.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행 채팅방 메시지 1개. 여행마다 채팅방이 하나라 trip 으로 묶는다.
 * 순서와 "새 메시지만 가져오기"는 id 로 한다.
 */
@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    // 보낸 사람. 도틀로(AI) 메시지는 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")   // MySQL enum 이면 종류를 더할 때 ddl update 가 안 바꿔 줘서 문자열로 둔다
    private ChatMessageType type;

    // 말풍선 글. 투표 카드처럼 글 없이 payload 만 있을 수 있다
    @Column(length = 500)
    private String text;

    // 종류별 추가 데이터(JSON). AI 는 카드·버튼, POLL 은 pollId
    @Column(columnDefinition = "json")
    private String payload;

    public static ChatMessage create(Trip trip, User sender, ChatMessageType type, String text, String payload) {
        ChatMessage message = new ChatMessage();
        message.trip = trip;
        message.sender = sender;
        message.type = type;
        message.text = text;
        message.payload = payload;
        return message;
    }
}
