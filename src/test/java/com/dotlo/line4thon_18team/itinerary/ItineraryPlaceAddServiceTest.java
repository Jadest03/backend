package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.itinerary.ItineraryValidationService.ValidationRun;
import com.dotlo.line4thon_18team.itinerary.edit.ItineraryDiff;
import com.dotlo.line4thon_18team.itinerary.edit.dto.ItineraryPlaceAddResult;
import com.dotlo.line4thon_18team.itinerary.edit.dto.ItineraryPlaceAddSuggestion;
import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.TripContext;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItineraryVersionView;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
import com.dotlo.line4thon_18team.place.entity.PlaceMatchStatus;
import com.dotlo.line4thon_18team.place.entity.SavedPlace;
import com.dotlo.line4thon_18team.place.repository.SavedPlaceRepository;
import com.dotlo.line4thon_18team.place.service.PlaceEnrichService;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 장소 하나 일정에 넣기 (COLLAB-ADD-001).
 *
 * <p>화면이 두 단계라서 메서드도 둘이다. suggest 는 "언제 넣을까요?"에 쓸 날짜와 추천 날을 주고,
 * add 는 사용자가 고른 날에 넣는다. 어느 순서에 넣는지는 {@link ItineraryEditServiceTest}에서 본다.
 */
class ItineraryPlaceAddServiceTest {

    private static final long TRIP = 1L;
    private static final long USER = 7L;

    // 1일차에 두는 북쪽(우메다), 2일차에 두는 남쪽(난바). 넣을 장소는 남쪽 바로 옆이다
    private static final String UMEDA = "place-umeda";
    private static final String DOTONBORI = "place-dotonbori";
    private static final String KUROMON = "place-kuromon";

    private static final ValidationResult VALID = ValidationResult.of(List.of(), List.of());

    private final PlaceEnrichService enrichService = Mockito.mock(PlaceEnrichService.class);
    private final SavedPlaceRepository savedPlaces = Mockito.mock(SavedPlaceRepository.class);
    private final ItineraryService itineraryService = Mockito.mock(ItineraryService.class);
    private final ItineraryEditService editService = Mockito.mock(ItineraryEditService.class);
    private final ItineraryValidationService validationService = Mockito.mock(ItineraryValidationService.class);

    private final ItineraryPlaceAddService service = new ItineraryPlaceAddService(
            enrichService, savedPlaces, itineraryService, editService, validationService);

    private final List<SavedPlace> places = new ArrayList<>();

    @BeforeEach
    void setUp() {
        place(UMEDA, "우메다 스카이빌딩", 34.7055, 135.4983);
        place(DOTONBORI, "도톤보리", 34.6687, 135.5013);
        place(KUROMON, "구로몬 시장", 34.6652, 135.5063);
        givenRun();
    }

    // ---- 언제 넣을까요 ----

    @Test
    void 가까운_장소가_있는_날을_추천한다() {
        givenCurrent(3, twoDays());

        ItineraryPlaceAddSuggestion suggestion = service.suggest(TRIP, USER, KUROMON);

        assertThat(suggestion.placeName()).isEqualTo("구로몬 시장");
        assertThat(suggestion.recommendedDay()).isEqualTo(2);   // 도톤보리와 같은 날
        assertThat(suggestion.days()).filteredOn(ItineraryPlaceAddSuggestion.Day::recommended)
                .extracting(ItineraryPlaceAddSuggestion.Day::day)
                .containsExactly(2);
    }

    @Test
    void 빠진_날_없이_여행_전체를_돌려준다() {
        givenCurrent(3, twoDays());

        ItineraryPlaceAddSuggestion suggestion = service.suggest(TRIP, USER, KUROMON);

        assertThat(suggestion.days()).extracting(ItineraryPlaceAddSuggestion.Day::day)
                .containsExactly(1, 2, 3, 4);
        assertThat(suggestion.days()).extracting(ItineraryPlaceAddSuggestion.Day::date)
                .containsExactly(OsakaFixtures.DAY1, OsakaFixtures.DAY2, OsakaFixtures.DAY3, OsakaFixtures.DAY4);
        assertThat(suggestion.days()).extracting(ItineraryPlaceAddSuggestion.Day::visitCount)
                .containsExactly(1, 1, 0, 0);
    }

    @Test
    void 날짜를_고르는_단계에서는_AI를_부르지도_저장하지도_않는다() {
        givenCurrent(3, twoDays());

        service.suggest(TRIP, USER, KUROMON);

        Mockito.verifyNoInteractions(editService);
        Mockito.verify(itineraryService, Mockito.never()).save(Mockito.anyLong(), Mockito.anyLong(),
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void 폐업한_장소는_날짜를_묻기_전에_걸러낸다() {
        place(KUROMON, "구로몬 시장", PlaceMatchStatus.CLOSED);

        assertThatThrownBy(() -> service.suggest(TRIP, USER, KUROMON))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLACE_NOT_PLANNABLE)
                .hasMessageContaining("폐업");
    }

    @Test
    void 이미_일정에_있으면_날짜를_묻지_않는다() {
        givenCurrent(3, twoDays());

        assertThatThrownBy(() -> service.suggest(TRIP, USER, DOTONBORI))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLACE_ALREADY_IN_ITINERARY);
    }

    // ---- 고른 날에 넣기 ----

    @Test
    void 고른_날에_넣고_새_버전으로_저장한다() {
        givenCurrent(3, twoDays());
        givenEdited(kuromonOnDay2(), "2일차 도톤보리 다음에 구로몬 시장을 넣었습니다.");
        givenSaveReturns(4);

        ItineraryPlaceAddResult result = service.add(TRIP, USER, KUROMON, 2, null);

        assertThat(result.placeName()).isEqualTo("구로몬 시장");
        assertThat(result.itinerary().version()).isEqualTo(4);
        assertThat(result.diff().changesOf(ItineraryDiff.ChangeKind.ADDED))
                .extracting(ItineraryDiff.ItemChange::placeId)
                .containsExactly(KUROMON);
        Mockito.verify(itineraryService).save(TRIP, USER, ItinerarySource.AI_EDITED, 3, kuromonOnDay2(), VALID);
    }

    @Test
    void 고른_날을_수정_요청_문장에_적는다() {
        givenCurrent(3, twoDays());
        givenEdited(kuromonOnDay2(), "넣었습니다.");
        givenSaveReturns(4);

        service.add(TRIP, USER, KUROMON, 2, "  저녁에 가고 싶어요  ");

        ArgumentCaptor<String> request = ArgumentCaptor.forClass(String.class);
        Mockito.verify(editService).edit(Mockito.eq(TRIP), Mockito.eq(USER), Mockito.any(), request.capture());
        assertThat(request.getValue()).contains("구로몬 시장").contains("2일차").contains("저녁에 가고 싶어요");
    }

    @Test
    void AI가_다른_날에_넣으면_저장하지_않는다() {
        // 사용자가 2일차를 눌렀는데 1일차에 들어가면 화면에 누른 것과 다른 결과가 남는다
        givenCurrent(3, twoDays());
        givenEdited(kuromonOnDay1(), "1일차가 더 자연스러워 그쪽에 넣었습니다.");

        assertThatThrownBy(() -> service.add(TRIP, USER, KUROMON, 2, null))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLACE_ADD_FAILED);

        Mockito.verify(itineraryService, Mockito.never()).save(Mockito.anyLong(), Mockito.anyLong(),
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void 날을_비우면_AI가_고른_날을_그대로_쓴다() {
        givenCurrent(3, twoDays());
        givenEdited(kuromonOnDay1(), "1일차에 넣었습니다.");
        givenSaveReturns(4);

        ItineraryPlaceAddResult result = service.add(TRIP, USER, KUROMON, null, null);

        assertThat(result.reason()).isEqualTo("1일차에 넣었습니다.");
        ArgumentCaptor<String> request = ArgumentCaptor.forClass(String.class);
        Mockito.verify(editService).edit(Mockito.eq(TRIP), Mockito.eq(USER), Mockito.any(), request.capture());
        assertThat(request.getValue()).contains("알맞은 날과 순서").doesNotContain("일차 안에서");
    }

    @Test
    void 여행_기간_밖의_날은_400이고_AI를_부르지_않는다() {
        givenCurrent(3, twoDays());

        assertThatThrownBy(() -> service.add(TRIP, USER, KUROMON, 5, null))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ITINERARY)
                .hasMessageContaining("4일");

        Mockito.verifyNoInteractions(editService);
    }

    @Test
    void 저장_장소에_담은_뒤에_일정을_읽는다() {
        // 구글 조회가 끝나야 좌표가 생기고, 그제야 동선을 잴 수 있다
        givenCurrent(3, twoDays());

        service.suggest(TRIP, USER, KUROMON);

        InOrder order = Mockito.inOrder(enrichService, savedPlaces, itineraryService);
        order.verify(enrichService).add(USER, TRIP, KUROMON);
        order.verify(savedPlaces).findFirstByTripIdAndPlaceId(TRIP, KUROMON);
        order.verify(itineraryService).getLatest(TRIP, USER);
    }

    // ---- given ----

    private void place(String placeId, String name, double lat, double lng) {
        SavedPlace place = Mockito.mock(SavedPlace.class);
        Mockito.when(place.getPlaceId()).thenReturn(placeId);
        Mockito.when(place.getName()).thenReturn(name);
        Mockito.when(place.getLatitude()).thenReturn(lat);
        Mockito.when(place.getLongitude()).thenReturn(lng);
        Mockito.when(place.getMatchStatus()).thenReturn(PlaceMatchStatus.MATCHED);
        places.add(place);
        Mockito.when(savedPlaces.findFirstByTripIdAndPlaceId(TRIP, placeId)).thenReturn(Optional.of(place));
    }

    private void place(String placeId, String name, PlaceMatchStatus status) {
        SavedPlace place = Mockito.mock(SavedPlace.class);
        Mockito.when(place.getName()).thenReturn(name);
        Mockito.when(place.getMatchStatus()).thenReturn(status);
        Mockito.when(savedPlaces.findFirstByTripIdAndPlaceId(TRIP, placeId)).thenReturn(Optional.of(place));
    }

    /** 숙소를 공항으로 두어, 방문이 없는 날의 기준점이 시내에서 멀어지게 한다. */
    private void givenRun() {
        User user = User.builder().googleSub("sub").email("a@b.com").name("주인").build();
        Trip trip = Trip.create(user, "오사카", OsakaFixtures.DAY1, OsakaFixtures.DAY4, Companion.ALONE, null);
        TripContext context = TripContext.withoutStays(OsakaFixtures.ZONE, OsakaFixtures.DAY1,
                OsakaFixtures.DAY4, OsakaFixtures.KIX, OsakaFixtures.KIX, null, null);
        Mockito.when(validationService.prepare(TRIP, USER))
                .thenReturn(new ValidationRun(trip, places, null, context, PreferenceProfile.defaults()));
    }

    private void givenCurrent(int version, ItineraryDraft draft) {
        Mockito.when(itineraryService.getLatest(TRIP, USER)).thenReturn(
                new ItineraryVersionView(version, ItinerarySource.AI_GENERATED, null, USER,
                        LocalDateTime.of(2026, 12, 1, 10, 0), draft, "{}"));
    }

    private void givenEdited(ItineraryDraft draft, String reason) {
        Mockito.when(editService.edit(Mockito.eq(TRIP), Mockito.eq(USER), Mockito.any(), Mockito.anyString()))
                .thenReturn(new EditedItinerary(draft, VALID, reason, 1));
    }

    private void givenSaveReturns(int version) {
        Mockito.when(itineraryService.save(Mockito.anyLong(), Mockito.anyLong(), Mockito.any(),
                        Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(call -> new ItineraryVersionView(version, ItinerarySource.AI_EDITED, version - 1, USER,
                        LocalDateTime.of(2026, 12, 1, 11, 0), call.getArgument(4), "{}"));
    }

    private static ItineraryDraft twoDays() {
        return ItineraryDraft.of(
                DayDraft.of(OsakaFixtures.DAY1, VisitDraft.of("v-umeda", UMEDA)),
                DayDraft.of(OsakaFixtures.DAY2, VisitDraft.of("v-dotonbori", DOTONBORI)));
    }

    private static ItineraryDraft kuromonOnDay2() {
        return ItineraryDraft.of(
                DayDraft.of(OsakaFixtures.DAY1, VisitDraft.of("v-umeda", UMEDA)),
                DayDraft.of(OsakaFixtures.DAY2, VisitDraft.of("v-dotonbori", DOTONBORI),
                        VisitDraft.of("v-kuromon", KUROMON)));
    }

    private static ItineraryDraft kuromonOnDay1() {
        return ItineraryDraft.of(
                DayDraft.of(OsakaFixtures.DAY1, VisitDraft.of("v-umeda", UMEDA),
                        VisitDraft.of("v-kuromon", KUROMON)),
                DayDraft.of(OsakaFixtures.DAY2, VisitDraft.of("v-dotonbori", DOTONBORI)));
    }
}
