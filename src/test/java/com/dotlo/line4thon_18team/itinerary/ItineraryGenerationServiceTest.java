package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.llm.FakeLlmClient;
import com.dotlo.line4thon_18team.global.llm.StructuredLlmExecutor;
import com.dotlo.line4thon_18team.global.llm.schema.SchemaLoader;
import com.dotlo.line4thon_18team.itinerary.ItineraryValidationService.ValidationRun;
import com.dotlo.line4thon_18team.itinerary.ai.PlanGenerationTask;
import com.dotlo.line4thon_18team.itinerary.validation.ItineraryValidator;
import com.dotlo.line4thon_18team.itinerary.validation.ValidationPolicy;
import com.dotlo.line4thon_18team.itinerary.validation.model.PlaceInfo;
import com.dotlo.line4thon_18team.itinerary.validation.support.FakePlaceInfoProvider;
import com.dotlo.line4thon_18team.itinerary.validation.support.FakeTravelTimeProvider;
import com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItineraryVersionView;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
import com.dotlo.line4thon_18team.place.entity.PlaceMatchStatus;
import com.dotlo.line4thon_18team.place.entity.SavedPlace;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.user.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.DOTONBORI;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.KUROMON;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.OSAKA_CASTLE;
import static com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures.UMEDA_SKY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI 일정 생성. 모델 응답은 가짜로 주고, 검증기는 실제 오사카 장소로 돌린다.
 */
class ItineraryGenerationServiceTest {

    private final FakeLlmClient llm = new FakeLlmClient();
    private final ItineraryValidationService validationService = Mockito.mock(ItineraryValidationService.class);
    private final ItineraryService itineraryService = Mockito.mock(ItineraryService.class);
    private final ItineraryGenerationService service = new ItineraryGenerationService(
            validationService,
            new StructuredLlmExecutor(llm, 3, 0, millis -> { }),
            new PlanGenerationTask(new SchemaLoader()),
            itineraryService);

    @Test
    void 장소를_일차별로_나누고_시각을_계산한다() {
        given(matched(DOTONBORI), matched(KUROMON), matched(OSAKA_CASTLE));
        llm.thenReturn("""
                {"days": [{"dayNumber": 1, "placeNumbers": [1, 2]},
                          {"dayNumber": 2, "placeNumbers": [3]},
                          {"dayNumber": 3, "placeNumbers": []},
                          {"dayNumber": 4, "placeNumbers": []}],
                 "excluded": []}
                """);

        GeneratedItinerary result = service.generate(1L, 7L);

        assertThat(result.attempts()).isEqualTo(1);
        assertThat(result.draft().days()).hasSize(4);
        assertThat(result.draft().days().get(0).visits()).extracting(v -> v.placeId())
                .containsExactly(DOTONBORI.placeId(), KUROMON.placeId());
        assertThat(result.validation().days().get(0).visits()).hasSize(2);
        assertThat(result.validation().summary().valid()).isTrue();
    }

    @Test
    void itemId를_새로_만들고_서로_겹치지_않는다() {
        given(matched(DOTONBORI), matched(KUROMON));
        llm.thenReturn("""
                {"days": [{"dayNumber": 1, "placeNumbers": [1]},
                          {"dayNumber": 2, "placeNumbers": [2]},
                          {"dayNumber": 3, "placeNumbers": []},
                          {"dayNumber": 4, "placeNumbers": []}],
                 "excluded": []}
                """);

        GeneratedItinerary result = service.generate(1L, 7L);

        List<String> itemIds = result.draft().days().stream()
                .flatMap(day -> day.visits().stream())
                .map(visit -> visit.itemId())
                .toList();
        assertThat(itemIds).hasSize(2).doesNotHaveDuplicates().allMatch(id -> id.startsWith("v-"));
    }

    @Test
    void 넣지_못한_장소는_place_id로_돌려준다() {
        given(matched(DOTONBORI), matched(KUROMON), matched(UMEDA_SKY));
        llm.thenReturn("""
                {"days": [{"dayNumber": 1, "placeNumbers": [1]},
                          {"dayNumber": 2, "placeNumbers": [2]},
                          {"dayNumber": 3, "placeNumbers": []},
                          {"dayNumber": 4, "placeNumbers": []}],
                 "excluded": [3]}
                """);

        GeneratedItinerary result = service.generate(1L, 7L);

        assertThat(result.excludedPlaceIds()).containsExactly(UMEDA_SKY.placeId());
    }

    @Test
    void 검증에_걸리면_문제를_알려주고_다시_배치한다() {
        given(matched(DOTONBORI), matched(OsakaFixtures.CAFE_CLOSED_MONDAY));
        // 1일차(월요일)에 휴무인 카페를 넣음 → 다시 배치
        llm.thenReturn("""
                {"days": [{"dayNumber": 1, "placeNumbers": [1, 2]},
                          {"dayNumber": 2, "placeNumbers": []},
                          {"dayNumber": 3, "placeNumbers": []},
                          {"dayNumber": 4, "placeNumbers": []}],
                 "excluded": []}
                """);
        llm.thenReturn("""
                {"days": [{"dayNumber": 1, "placeNumbers": [1]},
                          {"dayNumber": 2, "placeNumbers": [2]},
                          {"dayNumber": 3, "placeNumbers": []},
                          {"dayNumber": 4, "placeNumbers": []}],
                 "excluded": []}
                """);

        GeneratedItinerary result = service.generate(1L, 7L);

        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.validation().summary().valid()).isTrue();
        assertThat(llm.requests().get(1).messages().get(1).content())
                .contains("지난번 배치에서 아래 문제가 있었습니다")
                .contains("그날 휴무입니다");
    }

    @Test
    void 세_번_모두_걸리면_마지막_결과를_그대로_돌려준다() {
        given(matched(DOTONBORI), matched(OsakaFixtures.CAFE_CLOSED_MONDAY));
        String closedDay = """
                {"days": [{"dayNumber": 1, "placeNumbers": [1, 2]},
                          {"dayNumber": 2, "placeNumbers": []},
                          {"dayNumber": 3, "placeNumbers": []},
                          {"dayNumber": 4, "placeNumbers": []}],
                 "excluded": []}
                """;
        llm.thenReturn(closedDay).thenReturn(closedDay).thenReturn(closedDay);

        GeneratedItinerary result = service.generate(1L, 7L);

        assertThat(result.attempts()).isEqualTo(3);
        assertThat(result.validation().summary().valid()).isFalse();
        assertThat(result.draft().days().get(0).visits()).hasSize(2);
    }

    @Test
    void 매칭된_장소가_없으면_400이다() {
        given(pending(DOTONBORI));

        assertThatThrownBy(() -> service.generate(1L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("장소가 없습니다");
        assertThat(llm.callCount()).isZero();
    }

    @Test
    void 만든_일정을_AI_생성으로_저장한다() {
        given(matched(DOTONBORI), matched(KUROMON));
        llm.thenReturn("""
                {"days": [{"dayNumber": 1, "placeNumbers": [1]},
                          {"dayNumber": 2, "placeNumbers": [2]},
                          {"dayNumber": 3, "placeNumbers": []},
                          {"dayNumber": 4, "placeNumbers": []}],
                 "excluded": []}
                """);
        Mockito.when(itineraryService.save(Mockito.anyLong(), Mockito.anyLong(), Mockito.any(),
                        Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(call -> savedVersion(call.getArgument(4)));

        GeneratedItineraryVersion result = service.generateAndSave(1L, 7L);

        // 첫 생성이라 baseVersion 은 없다. 수정 반영과 달리 덮어쓸 기존 일정이 없기 때문
        Mockito.verify(itineraryService).save(Mockito.eq(1L), Mockito.eq(7L),
                Mockito.eq(ItinerarySource.AI_GENERATED), Mockito.isNull(), Mockito.any(), Mockito.any());
        assertThat(result.itinerary().version()).isEqualTo(1);
        assertThat(result.itinerary().draft().days()).hasSize(4);
    }

    @Test
    void 넣지_못한_장소는_저장_결과와_함께_돌려준다() {
        given(matched(DOTONBORI), matched(KUROMON), matched(UMEDA_SKY));
        llm.thenReturn("""
                {"days": [{"dayNumber": 1, "placeNumbers": [1]},
                          {"dayNumber": 2, "placeNumbers": [2]},
                          {"dayNumber": 3, "placeNumbers": []},
                          {"dayNumber": 4, "placeNumbers": []}],
                 "excluded": [3]}
                """);
        Mockito.when(itineraryService.save(Mockito.anyLong(), Mockito.anyLong(), Mockito.any(),
                        Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(call -> savedVersion(call.getArgument(4)));

        GeneratedItineraryVersion result = service.generateAndSave(1L, 7L);

        assertThat(result.excludedPlaceIds()).containsExactly(UMEDA_SKY.placeId());
    }

    @Test
    void 생성에_실패하면_저장하지_않는다() {
        given(pending(DOTONBORI));

        assertThatThrownBy(() -> service.generateAndSave(1L, 7L)).isInstanceOf(BusinessException.class);
        Mockito.verifyNoInteractions(itineraryService);
    }

    // ---- helpers ----

    private static ItineraryVersionView savedVersion(Object draft) {
        return new ItineraryVersionView(1, ItinerarySource.AI_GENERATED, null, 7L, LocalDateTime.now(),
                (com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft) draft, "{}");
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
        return savedPlace(place, PlaceMatchStatus.MATCHED, place.placeId());
    }

    private static SavedPlace pending(PlaceInfo place) {
        return savedPlace(place, PlaceMatchStatus.PENDING, null);
    }

    // SavedPlace 는 Takeout 용 생성 메서드만 있고 매칭 결과를 채울 방법이 없어서, 읽는 값만 가짜로 만든다
    private static SavedPlace savedPlace(PlaceInfo place, PlaceMatchStatus status, String placeId) {
        SavedPlace saved = Mockito.mock(SavedPlace.class);
        Mockito.when(saved.getMatchStatus()).thenReturn(status);
        Mockito.when(saved.getPlaceId()).thenReturn(placeId);
        Mockito.when(saved.getName()).thenReturn(place.name());
        Mockito.when(saved.getCategory()).thenReturn(place.category());
        return saved;
    }
}
