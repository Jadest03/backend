package com.dotlo.line4thon_18team.chat.repository;

import com.dotlo.line4thon_18team.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 폴링: 마지막으로 받은 id 다음부터 오래된 것 순으로 100개 (처음 들어오면 afterId=0)
    @EntityGraph(attributePaths = "sender")
    List<ChatMessage> findTop100ByTripIdAndIdGreaterThanOrderByIdAsc(Long tripId, Long afterId);

    // 여행 삭제용. 벌크 delete 를 쓰지 않는 이유는 ItineraryTripCleanupService 주석에 있다
    void deleteAllByTripId(Long tripId);
}
