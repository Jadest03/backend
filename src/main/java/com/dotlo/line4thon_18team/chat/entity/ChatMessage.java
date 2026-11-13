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
@Table(name = "trip_chat_message", indexes = @Index(name = "idx_trip_chat_message_trip_id", columnList = "trip_id,id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    // 보낸 사람. SYSTEM, AI 메시지는 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")   // MySQL enum 이면 종류를 더할 때 ddl update 가 안 바꿔 줘서 문자열로 둔다
    private ChatMessageType type;

    // 말풍선 글. 사람이 보내는 글은 500자까지 (ChatMessageSendRequest)
    @Column(columnDefinition = "TEXT")
    private String text;

    // 도틀로 카드·선택지(JSON). 서버는 열어보지 않고 받은 글자 그대로 저장해서 돌려준다
    @Column(columnDefinition = "LONGTEXT")
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
