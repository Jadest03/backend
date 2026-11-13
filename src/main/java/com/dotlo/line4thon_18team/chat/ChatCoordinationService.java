package com.dotlo.line4thon_18team.chat;

import com.dotlo.line4thon_18team.chat.ai.CoordinationInput;
import com.dotlo.line4thon_18team.chat.ai.CoordinationOutput;
import com.dotlo.line4thon_18team.chat.ai.CoordinationTask;
import com.dotlo.line4thon_18team.chat.dto.CoordinationCommand;
import com.dotlo.line4thon_18team.chat.dto.CoordinationResponse;
import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.global.llm.StructuredLlmExecutor;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 의견 조율 (COLLAB-COORD-001). 채팅방 대화를 읽고 사람별로 무엇을 원하는지 정리한 뒤,
 * 어떻게 조율할지 방향을 제안한다. 일정이나 장소는 건드리지 않는다.
 *
 * <p>AI 에는 사람을 번호로 넘기고 번호로 돌려받아 서버가 사용자 id 로 되돌린다. 화면에서는 요약이
 * 이름과 함께 나와서, 엉뚱한 사람 말이 붙으면 바로 티가 난다.
 *
 * <p>트랜잭션을 걸지 않는다. AI 응답을 기다리는 동안 DB 연결을 쥐고 있지 않기 위해서다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ChatCoordinationService {

    static final String OPTIONS_TITLE = "어떤 방향으로 조율해 볼까요?";
    static final String TALK_MORE_ID = "talk-more";
    static final String TALK_MORE_LABEL = "조금 더 이야기해 볼게요";

    private final TripService tripService;
    private final StructuredLlmExecutor llm;
    private final CoordinationTask coordinationTask;

    /**
     * @throws BusinessException 구성원이 아닌 여행·없는 여행(404), 정리할 대화가 하나도 남지 않음(400)
     */
    public CoordinationResponse coordinate(long tripId, long userId, List<CoordinationCommand.Message> messages) {
        Trip trip = tripService.findMemberTrip(userId, tripId);

        Map<Long, Integer> numberByUser = numberInSpeakingOrder(trip, messages);
        List<CoordinationInput.Line> lines = messages.stream()
                .filter(message -> numberByUser.containsKey(message.userId()))
                .map(message -> new CoordinationInput.Line(numberByUser.get(message.userId()), message.text()))
                .toList();
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_MESSAGES_TO_COORDINATE);
        }

        CoordinationOutput output = llm.execute(coordinationTask,
                new CoordinationInput(trip.getCity(), lines)).value();

        return new CoordinationResponse(output.text(),
                new CoordinationResponse.Card(CoordinationResponse.OPINION_SUMMARY,
                        summaries(output, numberByUser)),
                options(output));
    }

    /**
     * 처음 말한 순서대로 1번부터 붙인다. 같은 사람이 여러 번 말해도 번호는 하나다.
     *
     * <p>여행을 나갔거나 탈퇴한 사람의 지난 메시지는 채팅방에 그대로 남는다. 그 사람에게는 번호를 주지
     * 않고 건너뛴다. 지금 함께 가는 사람들끼리 조율하는 자리이기도 하고, 번호가 없으면 요약에
     * 그 사람 줄이 생기지 않아 화면에서 이름 없는 줄이 나오는 일도 없다.
     */
    private Map<Long, Integer> numberInSpeakingOrder(Trip trip, List<CoordinationCommand.Message> messages) {
        Map<Long, Integer> numberByUser = new LinkedHashMap<>();
        Set<Long> left = new HashSet<>();   // 같은 사람을 여러 번 조회하지 않으려고 기억해 둔다
        for (CoordinationCommand.Message message : messages) {
            Long writer = message.userId();
            if (numberByUser.containsKey(writer) || left.contains(writer)) {
                continue;
            }
            if (tripService.isMember(trip, writer)) {
                numberByUser.put(writer, numberByUser.size() + 1);
            } else {
                left.add(writer);
            }
        }
        return numberByUser;
    }

    /** 번호를 사용자 id 로 되돌린다. 모르는 번호는 CoordinationTask 가 걸러서 여기까지 오지 않는다. */
    private static List<CoordinationResponse.Summary> summaries(CoordinationOutput output,
                                                                Map<Long, Integer> numberByUser) {
        Map<Integer, Long> userByNumber = new LinkedHashMap<>();
        numberByUser.forEach((user, number) -> userByNumber.put(number, user));
        List<CoordinationResponse.Summary> summaries = new ArrayList<>();
        for (CoordinationOutput.Summary summary : output.summaries()) {
            Long user = userByNumber.get(summary.memberNumber());
            if (user != null) {
                summaries.add(new CoordinationResponse.Summary(user, summary.point()));
            }
        }
        return List.copyOf(summaries);
    }

    /** AI 가 낸 방향 뒤에 "조금 더 이야기해 볼게요"를 붙인다. 아직 정하지 않을 자유는 서버가 보장한다. */
    private static CoordinationResponse.Options options(CoordinationOutput output) {
        List<CoordinationResponse.Item> items = new ArrayList<>();
        for (int i = 0; i < output.directions().size(); i++) {
            items.add(new CoordinationResponse.Item("d" + (i + 1), output.directions().get(i)));
        }
        items.add(new CoordinationResponse.Item(TALK_MORE_ID, TALK_MORE_LABEL));
        return new CoordinationResponse.Options(OPTIONS_TITLE, List.copyOf(items));
    }
}
