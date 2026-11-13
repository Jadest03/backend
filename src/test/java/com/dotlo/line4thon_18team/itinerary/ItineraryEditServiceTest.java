package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.global.llm.FakeLlmClient;
import com.dotlo.line4thon_18team.global.llm.StructuredLlmExecutor;
import com.dotlo.line4thon_18team.global.llm.schema.SchemaLoader;
import com.dotlo.line4thon_18team.itinerary.ItineraryValidationService.ValidationRun;
import com.dotlo.line4thon_18team.itinerary.ai.PlanEditTask;
import com.dotlo.line4thon_18team.itinerary.validation.ItineraryValidator;
import com.dotlo.line4thon_18team.itinerary.validation.ValidationPolicy;
import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.PlaceInfo;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.support.FakePlaceInfoProvider;
import com.dotlo.line4thon_18team.itinerary.validation.support.FakeTravelTimeProvider;
import com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures;
import com.dotlo.line4thon_18team.place.entity.PlaceMatchStatus;
import com.dotlo.line4thon_18team.place.entity.SavedPlace;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.user.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.DOTONBORI;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.KUROMON;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.OSAKA_CASTLE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 일정 텍스트 수정. 모델 응답은 가짜로 주고, 검증기는 실제 오사카 장소로 돌린다.
 */
class ItineraryEditServiceTest {

    private final FakeLlmClient llm = new FakeLlmClient();
    private final ItineraryValidationService validationService = Mockito.mock(ItineraryValidationService.class);
    private final ItineraryEditService service = new ItineraryEditService(
            validationService,
            new StructuredLlmExecutor(llm, 3, 0, millis -> { }),
            new PlanEditTask(new SchemaLoader()));

    @Test
    void 수정_이유를_그대로_돌려준다() {
        given(matched(DOTONBORI), matched(KUROMON));
        llm.thenReturn("""
                {"days": [{"dayNumber": 1, "visits": [{"itemId": "v-aaa", "placeNumber": null}]},
                          {"dayNumber": 2, "visits": [{"itemId": "v-bbb", "placeNumber": null}]},
                          {"dayNumber": 3, "visits": []},
                          {"dayNumber": 4, "visits": []}], "reason": "둘째 날 일정을 하나 줄였습니다."}
                """);

        EditedItinerary result = service.edit(1L, 7L, current(), "둘째 날 줄여 주세요");

        assertThat(result.reason()).isEqualTo("둘째 날 일정을 하나 줄였습니다.");
    }

    @Test
    void 손대지_않은_방문은_itemId가_그대로다() {
        given(matched(DOTONBORI), matched(KUROMON), matched(OSAKA_CASTLE));
        llm.thenReturn("""
                {"days": [{"dayNumber": 1, "visits": [{"itemId": "v-aaa", "placeNumber": null}]},
                          {"dayNumber": 2, "visits": [{"itemId": "v-bbb", "placeNumber": null}]},
                          {"dayNumber": 3, "visits": []},
                          {"dayNumber": 4, "visits": []}], "reason": "요청대로 고쳤습니다."}
                """);

        EditedItinerary result = service.edit(1L, 7L, current(), "둘째 날로 하나 옮겨 주세요");

        assertThat(result.attempts()).isEqualTo(1);
        assertThat(result.draft().days().get(0).visits()).extracting(VisitDraft::itemId).containsExactly("v-aaa");
        assertThat(result.draft().days().get(1).visits()).extracting(VisitDraft::itemId).containsExactly("v-bbb");
    }

    @Test
    void 체류시간과_예약_시각을_잃지_않는다() {
        given(matched(DOTONBORI), matched(KUROMON));
        ItineraryDraft current = ItineraryDraft.of(
                DayDraft.of(OsakaFixtures.DAY1, VisitDraft.of("v-aaa", DOTONBORI.placeId()).withStayMinutes(120)),
                DayDraft.of(OsakaFixtures.DAY2, VisitDraft.of("v-bbb", KUROMON.placeId())));
        llm.thenReturn("""
                {"days": [{"dayNumber": 1, "visits": []},
                          {"dayNumber": 2, "visits": [{"itemId": "v-bbb", "placeNumber": null},
                                                      {"itemId": "v-aaa", "placeNumber": null}]},
                          {"dayNumber": 3, "visits": []},
                          {"dayNumber": 4, "visits": []}], "reason": "요청대로 고쳤습니다."}
                """);

        EditedItinerary result = service.edit(1L, 7L, current, "도톤보리를 둘째 날로");

        VisitDraft moved = result.draft().days().get(1).visits().get(1);
        assertThat(moved.itemId()).isEqualTo("v-aaa");
        assertThat(moved.stayMinutes()).isEqualTo(120);
    }

    @Test
    void 새로_넣는_장소는_itemId를_새로_만든다() {
        given(matched(DOTONBORI), matched(KUROMON), matched(OSAKA_CASTLE));
        // 지금 일정에 없는 오사카성이 1번으로 들어간다
        llm.thenReturn("""
                {"days": [{"dayNumber": 1, "visits": [{"itemId": "v-aaa", "placeNumber": null},
                                                      {"itemId": null, "placeNumber": 1}]},
                          {"dayNumber": 2, "visits": [{"itemId": "v-bbb", "placeNumber": null}]},
                          {"dayNumber": 3, "visits": []},
                          {"dayNumber": 4, "visits": []}], "reason": "요청대로 고쳤습니다."}
                """);

        EditedItinerary result = service.edit(1L, 7L, current(), "첫날에 오사카성 넣어 주세요");

        List<VisitDraft> firstDay = result.draft().days().get(0).visits();
        assertThat(firstDay).hasSize(2);
        assertThat(firstDay.get(1).placeId()).isEqualTo(OSAKA_CASTLE.placeId());
        assertThat(firstDay.get(1).itemId()).startsWith("v-").isNotEqualTo("v-aaa");
    }

    @Test
    void 지금_일정에_있는_장소는_추가_목록에_넣지_않는다() {
        given(matched(DOTONBORI), matched(KUROMON), matched(OSAKA_CASTLE));
        llm.thenReturn(emptyPlan());

        service.edit(1L, 7L, current(), "아무거나");

        String prompt = llm.requests().get(0).messages().get(1).content();
        assertThat(prompt).contains("추가할 수 있는 장소 1곳")
                .contains(OSAKA_CASTLE.name())
                .contains("v-aaa " + DOTONBORI.name());
    }

    @Test
    void 검증에_걸리면_문제를_알려주고_다시_고친다() {
        given(matched(DOTONBORI), matched(OsakaFixtures.CAFE_CLOSED_MONDAY));
        ItineraryDraft current = ItineraryDraft.of(
                DayDraft.of(OsakaFixtures.DAY1, VisitDraft.of("v-aaa", DOTONBORI.placeId())),
                DayDraft.of(OsakaFixtures.DAY2,
                        VisitDraft.of("v-ccc", OsakaFixtures.CAFE_CLOSED_MONDAY.placeId())));
        // 1일차(월요일)에 휴무인 카페를 옮겨 놓음 → 다시 고침
        llm.thenReturn("""
                {"days": [{"dayNumber": 1, "visits": [{"itemId": "v-aaa", "placeNumber": null},
                                                      {"itemId": "v-ccc", "placeNumber": null}]},
                          {"dayNumber": 2, "visits": []},
                          {"dayNumber": 3, "visits": []},
                          {"dayNumber": 4, "visits": []}], "reason": "요청대로 고쳤습니다."}
                """);
        llm.thenReturn("""
                {"days": [{"dayNumber": 1, "visits": [{"itemId": "v-aaa", "placeNumber": null}]},
                          {"dayNumber": 2, "visits": [{"itemId": "v-ccc", "placeNumber": null}]},
                          {"dayNumber": 3, "visits": []},
                          {"dayNumber": 4, "visits": []}], "reason": "요청대로 고쳤습니다."}
                """);

        EditedItinerary result = service.edit(1L, 7L, current, "카페를 첫날로");

        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.validation().summary().valid()).isTrue();
        assertThat(llm.requests().get(1).messages().get(1).content())
                .contains("지난번 수정안에서 아래 문제가 있었습니다")
                .contains("그날 휴무입니다");
    }

    @Test
    void 세_번_모두_걸리면_마지막_결과를_그대로_돌려준다() {
        given(matched(DOTONBORI), matched(OsakaFixtures.CAFE_CLOSED_MONDAY));
        ItineraryDraft current = ItineraryDraft.of(
                DayDraft.of(OsakaFixtures.DAY1, VisitDraft.of("v-aaa", DOTONBORI.placeId())),
                DayDraft.of(OsakaFixtures.DAY2,
                        VisitDraft.of("v-ccc", OsakaFixtures.CAFE_CLOSED_MONDAY.placeId())));
        String closedDay = """
                {"days": [{"dayNumber": 1, "visits": [{"itemId": "v-aaa", "placeNumber": null},
                                                      {"itemId": "v-ccc", "placeNumber": null}]},
                          {"dayNumber": 2, "visits": []},
                          {"dayNumber": 3, "visits": []},
                          {"dayNumber": 4, "visits": []}], "reason": "요청대로 고쳤습니다."}
                """;
        llm.thenReturn(closedDay).thenReturn(closedDay).thenReturn(closedDay);

        EditedItinerary result = service.edit(1L, 7L, current, "카페를 첫날로");

        assertThat(result.attempts()).isEqualTo(3);
        assertThat(result.validation().summary().valid()).isFalse();
    }

    // ---- 도움 함수 ----

    private static String emptyPlan() {
        return """
                {"days": [{"dayNumber": 1, "visits": [{"itemId": "v-aaa", "placeNumber": null}]},
                          {"dayNumber": 2, "visits": [{"itemId": "v-bbb", "placeNumber": null}]},
                          {"dayNumber": 3, "visits": []},
                          {"dayNumber": 4, "visits": []}], "reason": "요청대로 고쳤습니다."}
                """;
    }

    /** 1일차 도톤보리(v-aaa), 2일차 구로몬(v-bbb) */
    private static ItineraryDraft current() {
        return ItineraryDraft.of(
                DayDraft.of(OsakaFixtures.DAY1, VisitDraft.of("v-aaa", DOTONBORI.placeId())),
                DayDraft.of(OsakaFixtures.DAY2, VisitDraft.of("v-bbb", KUROMON.placeId())));
    }

    private void given(SavedPlace... places) {
        FakePlaceInfoProvider provider = OsakaFixtures.places();
        ItineraryValidator validator = new ItineraryValidator(provider,
                FakeTravelTimeProvider.fixedMinutes(15), ValidationPolicy.defaults());
        ValidationRun run = new ValidationRun(trip(), List.of(places), validator,
                OsakaFixtures.trip(), PreferenceProfile.defaults());
        Mockito.when(validationService.prepare(1L, 7L)).thenReturn(run);
    }

    private static Trip trip() {
        User user = User.builder().googleSub("sub").email("a@b.com").name("주인").build();
        return Trip.create(user, "오사카", OsakaFixtures.DAY1, OsakaFixtures.DAY4, Companion.ALONE, null);
    }

    private static SavedPlace matched(PlaceInfo place) {
        SavedPlace saved = Mockito.mock(SavedPlace.class);
        Mockito.when(saved.getMatchStatus()).thenReturn(PlaceMatchStatus.MATCHED);
        Mockito.when(saved.getPlaceId()).thenReturn(place.placeId());
        Mockito.when(saved.getName()).thenReturn(place.name());
        Mockito.when(saved.getCategory()).thenReturn(place.category());
        return saved;
    }
}
