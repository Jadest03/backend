package com.dotlo.line4thon_18team.chat.poll.ai;

import com.dotlo.line4thon_18team.global.llm.LlmMessage;
import com.dotlo.line4thon_18team.global.llm.LlmTask;
import com.dotlo.line4thon_18team.global.llm.schema.SchemaLoader;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * COLLAB-POLL-002 (2/2) 후보 고르기. 서버가 검색해 온 장소 중 조율 방향에 맞는 곳을 고르고
 * 이름 밑에 붙일 유형을 한 단어로 쓴다.
 *
 * <p>검색 상위 몇 개를 그냥 쓰지 않는 이유는, 검색 순위가 조율 방향과 무관하기 때문이다.
 * "비용이 적은 대체 장소"를 찾는데 유료 시설이 1위로 나오면 후보가 엉뚱해진다.
 */
public class PollPickTask implements LlmTask<PollPickInput, PollPickOutput> {

    public static final String NAME = "poll-candidate-pick";
    public static final String SCHEMA_LOCATION = "llm/schemas/poll-candidate-pick.schema.json";

    static final int MAX_TAGLINE_LENGTH = 12;

    static final String SYSTEM_PROMPT = """
            너는 여행 일정 앱 dotlo의 '투표 후보 고르기' 도우미다. 서버가 검색해 온 장소 중
            동행자들이 정한 조율 방향에 맞는 곳을 고른다.

            규칙
            1. 장소는 번호로만 답한다. 목록에 없는 번호를 쓰지 않는다.
            2. 정해진 개수만큼 고른다. 더도 덜도 안 된다. 같은 번호를 두 번 쓰지 않는다.
            3. 서로 다른 종류를 섞는다. 같은 유형만 고르면 투표할 맛이 없다.
            4. 조율 방향에 맞지 않는 곳은 고르지 않는다. 검색 순위가 높아도 마찬가지다.
            5. tagline 은 그 장소가 무엇인지 한 단어로 쓴다. 공원, 수족관, 전망대, 시장, 미술관처럼.
               값·영업시간·평점·추천 문구는 쓰지 않는다. 우리가 가진 정보가 아니라서 틀릴 수 있다.
               무엇인지 확실하지 않으면 "관광지"처럼 넓은 말을 쓴다.
            """;

    private final JsonNode schema;

    public PollPickTask(SchemaLoader schemaLoader) {
        this.schema = schemaLoader.load(SCHEMA_LOCATION);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public JsonNode schema() {
        return schema;
    }

    @Override
    public Class<PollPickOutput> outputType() {
        return PollPickOutput.class;
    }

    @Override
    public List<LlmMessage> buildMessages(PollPickInput input) {
        return List.of(LlmMessage.system(SYSTEM_PROMPT), LlmMessage.user(userPrompt(input)));
    }

    static String userPrompt(PollPickInput input) {
        StringBuilder prompt = new StringBuilder()
                .append("정한 조율 방향: ").append(input.direction()).append('\n')
                .append("고를 개수: ").append(input.pickCount()).append("곳\n")
                .append("\n검색해 온 장소 ").append(input.places().size()).append("곳\n");
        for (PollPickInput.Found place : input.places()) {
            prompt.append(place.number()).append(". ").append(place.name());
            if (place.address() != null && !place.address().isBlank()) {
                prompt.append(" (").append(place.address()).append(')');
            }
            prompt.append('\n');
        }
        return prompt.toString();
    }

    @Override
    public PollPickOutput normalize(PollPickOutput output) {
        List<PollPickOutput.Pick> picks = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        for (PollPickOutput.Pick pick : output.picks()) {
            if (seen.add(pick.number())) {
                picks.add(pick);
            }
        }
        return new PollPickOutput(picks);
    }

    @Override
    public List<String> validate(PollPickInput input, PollPickOutput output) {
        List<String> problems = new ArrayList<>();
        List<Integer> unknown = output.picks().stream()
                .map(PollPickOutput.Pick::number)
                .filter(number -> !input.hasNumber(number))
                .distinct()
                .toList();
        if (!unknown.isEmpty()) {
            problems.add("목록에 없는 장소 번호입니다: " + unknown
                    + " (1~" + input.places().size() + "번만 쓰세요)");
        }
        if (output.picks().size() != input.pickCount()) {
            problems.add(input.pickCount() + "곳을 골라야 합니다 (받은 개수: " + output.picks().size() + ")");
        }
        output.picks().stream()
                .filter(pick -> pick.tagline().length() > MAX_TAGLINE_LENGTH)
                .findFirst()
                .ifPresent(pick -> problems.add("tagline 은 " + MAX_TAGLINE_LENGTH
                        + "자 이하 한 단어여야 합니다: " + pick.tagline()));
        return problems;
    }
}
