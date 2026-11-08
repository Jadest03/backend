package com.dotlo.line4thon_18team.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.place.PlaceCategory;
import com.dotlo.line4thon_18team.place.PlaceMatcher;
import com.dotlo.line4thon_18team.place.PlaceMatcher.Candidate;
import com.dotlo.line4thon_18team.place.PlaceMatcher.MatchResult;
import com.dotlo.line4thon_18team.place.PlaceMatcher.MatchStatus;
import com.dotlo.line4thon_18team.place.PlaceMatcher.PlaceInput;
import com.dotlo.line4thon_18team.place.entity.PlaceMatchStatus;
import com.dotlo.line4thon_18team.place.entity.PlaceSource;
import com.dotlo.line4thon_18team.place.entity.SavedPlace;
import com.dotlo.line4thon_18team.place.repository.SavedPlaceRepository;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.service.TripService;
import com.dotlo.line4thon_18team.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

// 매칭 결과 → 저장 장소 상태. 구글·DB 없이 PlaceMatcher 결과를 직접 넣어서 확인
class PlaceEnrichServiceTest {

    private final TripService tripService = mock(TripService.class);
    private final PlaceMatcher placeMatcher = mock(PlaceMatcher.class);
    private final SavedPlaceRepository repository = mock(SavedPlaceRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final PlaceEnrichService service = new PlaceEnrichService(tripService, placeMatcher, repository,
            new TransactionTemplate(mock(PlatformTransactionManager.class)), entityManager);
    private Trip trip;

    @BeforeEach
    void setUp() {
        LocalDate start = LocalDate.of(2026, 11, 10);
        trip = Trip.create(User.builder().googleSub("s").email("a@b.c").name("n").build(),
                "오사카", start, start.plusDays(2), Companion.ALONE, null);
        ReflectionTestUtils.setField(trip, "id", 7L);
        when(tripService.findOwnedTrip(1L, 7L)).thenReturn(trip);
        when(entityManager.find(Trip.class, 7L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(trip);
    }

    @Test
    void 매칭_결과에_따라_장소_정보와_상태를_채운다() {
        SavedPlace ichiran = place(1L, "이치란", "리스트");
        SavedPlace closed = place(2L, "폐업한 가게", "리스트");
        SavedPlace himeji = place(3L, "히메지성", "리스트");
        SavedPlace review = place(4L, "이치란 라멘", "리스트");
        SavedPlace notFound = place(5L, "ㅁㄴㅇㄹ", "리스트");
        pending(ichiran, closed, himeji, review, notFound);
        googleReturns(Map.of(
                1L, matched(1L, new Candidate("p1", "이치란 우메다", "오사카", 34.70, 135.50), PlaceCategory.FOOD, false),
                2L, matched(2L, new Candidate("p2", "폐업한 가게", "오사카", 34.69, 135.50), PlaceCategory.CAFE, true),
                3L, matched(3L, new Candidate("p3", "히메지성", "히메지", 34.84, 134.69), PlaceCategory.CULTURE, false),  // 오사카에서 약 75km
                4L, new MatchResult(4L, MatchStatus.NEEDS_REVIEW, null, List.of(), null, false),
                5L, new MatchResult(5L, MatchStatus.NOT_FOUND, null, List.of(), null, false)));

        service.enrich(1L, 7L);

        assertThat(ichiran.getMatchStatus()).isEqualTo(PlaceMatchStatus.MATCHED);
        assertThat(ichiran.getName()).isEqualTo("이치란");   // 사용자가 저장한 이름은 그대로
        assertThat(ichiran.getPlaceId()).isEqualTo("p1");
        assertThat(ichiran.getAddress()).isEqualTo("오사카");
        assertThat(ichiran.getCategory()).isEqualTo(PlaceCategory.FOOD);
        assertThat(closed.getMatchStatus()).isEqualTo(PlaceMatchStatus.CLOSED);
        assertThat(himeji.getMatchStatus()).isEqualTo(PlaceMatchStatus.OUT_OF_CITY);
        assertThat(himeji.getCategory()).isEqualTo(PlaceCategory.CULTURE);
        assertThat(review.getMatchStatus()).isEqualTo(PlaceMatchStatus.NEEDS_REVIEW);
        assertThat(review.getPlaceId()).isNull();
        assertThat(notFound.getMatchStatus()).isEqualTo(PlaceMatchStatus.NOT_FOUND);
    }

    @Test
    void 구글_오류가_나면_앞_장소_결과는_남고_남은_장소는_PENDING이다() {
        SavedPlace first = place(1L, "이치란", "리스트");
        SavedPlace second = place(2L, "하루카스", "리스트");
        pending(first, second);
        when(placeMatcher.matchPlaces(any(), anyDouble(), anyDouble()))
                .thenReturn(List.of(matched(1L, new Candidate("p1", "이치란", "오사카", 34.70, 135.50), PlaceCategory.FOOD, false)))
                .thenThrow(new RestClientException("quota"));

        assertThatThrownBy(() -> service.enrich(1L, 7L)).isInstanceOf(RestClientException.class);

        assertThat(first.getMatchStatus()).isEqualTo(PlaceMatchStatus.MATCHED);
        assertThat(second.getMatchStatus()).isEqualTo(PlaceMatchStatus.PENDING);
    }

    @Test
    void 같은_placeId가_이미_있으면_리스트_이름을_합치고_이_행은_지운다() {
        SavedPlace existing = place(1L, "이치란", "오사카 맛집");
        SavedPlace pin = place(2L, "이치란 핀", "라멘");
        pending(pin);
        when(repository.findFirstByTripIdAndPlaceId(7L, "p1")).thenReturn(Optional.of(existing));
        googleReturns(Map.of(
                2L, matched(2L, new Candidate("p1", "이치란", "오사카", 34.70, 135.50), PlaceCategory.FOOD, false)));

        service.enrich(1L, 7L);

        assertThat(existing.getListNames()).containsExactlyInAnyOrder("오사카 맛집", "라멘");
        verify(repository).delete(pin);
        verify(entityManager).find(Trip.class, 7L, LockModeType.PESSIMISTIC_WRITE);   // 직접 추가와 같은 여행 잠금
    }

    @Test
    void 합칠_때_지우는_행의_메모를_옮긴다() {
        SavedPlace existing = place(1L, "이치란", "오사카 맛집", "줄 김");
        SavedPlace pin = place(2L, "34.7055, 135.4975", "라멘", "야간 영업");
        SavedPlace noNote = place(3L, "이치란 핀", "라멘2", null);
        pending(pin, noNote);
        when(repository.findFirstByTripIdAndPlaceId(7L, "p1")).thenReturn(Optional.of(existing));
        Candidate ichiran = new Candidate("p1", "이치란", "오사카", 34.70, 135.50);
        googleReturns(Map.of(
                2L, matched(2L, ichiran, PlaceCategory.FOOD, false),
                3L, matched(3L, ichiran, PlaceCategory.FOOD, false)));

        service.enrich(1L, 7L);

        assertThat(existing.getNote()).isEqualTo("줄 김\n야간 영업");
        assertThat(existing.getListNames()).containsExactlyInAnyOrder("오사카 맛집", "라멘", "라멘2");
    }

    @Test
    void 메모가_없는_행에_합치면_옮긴_메모가_그대로_들어간다() {
        SavedPlace existing = place(1L, "34.7055, 135.4975", "라멘", null);
        SavedPlace named = place(2L, "이치란", "오사카 맛집", "줄 김");
        pending(named);
        when(repository.findFirstByTripIdAndPlaceId(7L, "p1")).thenReturn(Optional.of(existing));
        googleReturns(Map.of(2L, matched(2L, new Candidate("p1", "이치란", "오사카", 34.70, 135.50), PlaceCategory.FOOD, false)));

        service.enrich(1L, 7L);

        assertThat(existing.getNote()).isEqualTo("줄 김");
    }

    @Test
    void 후보를_고르면_확정되고_좌표_이름은_구글_이름으로_바뀐다() {
        SavedPlace pin = place(1L, "34.6463062, 135.5133091", "리스트");
        pin.updateMatchStatus(PlaceMatchStatus.NEEDS_REVIEW);
        when(repository.findById(1L)).thenReturn(Optional.of(pin));
        when(placeMatcher.details(1L, "p1")).thenReturn(
                matched(1L, new Candidate("p1", "하루카스 300", "오사카 아베노", 34.646, 135.513), PlaceCategory.SIGHTSEEING, false));

        service.match(1L, 7L, 1L, "p1");

        assertThat(pin.getMatchStatus()).isEqualTo(PlaceMatchStatus.MATCHED);
        assertThat(pin.getName()).isEqualTo("하루카스 300");
        assertThat(pin.getPlaceId()).isEqualTo("p1");
        assertThat(pin.getCategory()).isEqualTo(PlaceCategory.SIGHTSEEING);
    }

    @Test
    void 고른_장소가_도시_밖이면_OUT_OF_CITY다() {
        SavedPlace place = place(1L, "기요미즈데라", "리스트");
        place.updateMatchStatus(PlaceMatchStatus.NEEDS_REVIEW);
        when(repository.findById(1L)).thenReturn(Optional.of(place));
        when(placeMatcher.details(1L, "p3")).thenReturn(
                matched(1L, new Candidate("p3", "히메지성", "히메지", 34.84, 134.69), PlaceCategory.CULTURE, false));

        service.match(1L, 7L, 1L, "p3");

        assertThat(place.getMatchStatus()).isEqualTo(PlaceMatchStatus.OUT_OF_CITY);
        assertThat(place.getName()).isEqualTo("기요미즈데라");
    }

    @Test
    void 확인_필요가_아닌_장소는_고를_수_없고_구글을_부르지_않는다() {
        SavedPlace place = place(1L, "이치란", "리스트");   // PENDING
        when(repository.findById(1L)).thenReturn(Optional.of(place));

        assertThatThrownBy(() -> service.match(1L, 7L, 1L, "p1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PLACE_NOT_REVIEWABLE);
        verify(placeMatcher, never()).details(any(), any());
    }

    @Test
    void 다른_여행의_장소면_404다() {
        Trip other = Trip.create(User.builder().googleSub("o").email("o@b.c").name("o").build(),
                "오사카", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12), Companion.ALONE, null);
        ReflectionTestUtils.setField(other, "id", 8L);
        SavedPlace othersPlace = SavedPlace.fromTakeout(other, null, "남의 장소", null, null, null, null, "리스트");
        ReflectionTestUtils.setField(othersPlace, "id", 1L);
        othersPlace.updateMatchStatus(PlaceMatchStatus.NEEDS_REVIEW);
        when(repository.findById(1L)).thenReturn(Optional.of(othersPlace));

        assertThatThrownBy(() -> service.match(1L, 7L, 1L, "p1"))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SAVED_PLACE_NOT_FOUND);
        verify(placeMatcher, never()).details(any(), any());
    }

    @Test
    void 보강이_두_번_동시에_돌아_이미_처리된_장소면_건드리지_않는다() {
        SavedPlace ichiran = place(1L, "이치란", "오사카 맛집");
        pending(ichiran);
        // 다른 요청이 먼저 이 장소를 매칭해서 저장한 상태
        ichiran.applyMatch("이치란", "p1", "오사카", 34.70, 135.50, PlaceCategory.FOOD, PlaceMatchStatus.MATCHED);
        when(repository.findFirstByTripIdAndPlaceId(7L, "p1")).thenReturn(Optional.of(ichiran));
        googleReturns(Map.of(
                1L, matched(1L, new Candidate("p1", "이치란", "오사카", 34.70, 135.50), PlaceCategory.FOOD, false)));

        service.enrich(1L, 7L);

        verify(repository, never()).delete(any());
        assertThat(ichiran.getMatchStatus()).isEqualTo(PlaceMatchStatus.MATCHED);
    }

    @Test
    void 직접_추가하면_직접_추가_리스트로_매칭된_장소가_저장된다() {
        when(placeMatcher.details(null, "p1")).thenReturn(
                matched(null, new Candidate("p1", "하루카스 300", "오사카 아베노", 34.646, 135.513), PlaceCategory.SIGHTSEEING, false));

        service.add(1L, 7L, "p1");

        ArgumentCaptor<SavedPlace> saved = ArgumentCaptor.forClass(SavedPlace.class);
        verify(repository).save(saved.capture());
        SavedPlace p = saved.getValue();
        assertThat(p.getName()).isEqualTo("하루카스 300");
        assertThat(p.getPlaceId()).isEqualTo("p1");
        assertThat(p.getSource()).isEqualTo(PlaceSource.MANUAL_SEARCH);
        assertThat(p.getListNames()).containsExactly("직접 추가");
        assertThat(p.getCategory()).isEqualTo(PlaceCategory.SIGHTSEEING);
        assertThat(p.getMatchStatus()).isEqualTo(PlaceMatchStatus.MATCHED);
    }

    @Test
    void 직접_추가한_장소가_폐업이면_CLOSED로_저장된다() {
        when(placeMatcher.details(null, "p2")).thenReturn(
                matched(null, new Candidate("p2", "폐업한 가게", "오사카", 34.69, 135.50), PlaceCategory.CAFE, true));

        service.add(1L, 7L, "p2");

        ArgumentCaptor<SavedPlace> saved = ArgumentCaptor.forClass(SavedPlace.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getMatchStatus()).isEqualTo(PlaceMatchStatus.CLOSED);
    }

    @Test
    void 이미_있는_장소를_직접_추가하면_구글을_부르지_않고_직접_추가_리스트_이름만_붙인다() {
        SavedPlace existing = place(1L, "이치란", "오사카 맛집");
        when(repository.findFirstByTripIdAndPlaceId(7L, "p1")).thenReturn(Optional.of(existing));

        service.add(1L, 7L, "p1");

        assertThat(existing.getListNames()).containsExactlyInAnyOrder("오사카 맛집", "직접 추가");
        verify(placeMatcher, never()).details(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void 구글을_기다리는_사이_같은_장소가_들어왔으면_새로_만들지_않고_리스트_이름만_붙인다() {
        SavedPlace existing = place(1L, "이치란", "오사카 맛집");
        when(placeMatcher.details(null, "p1")).thenReturn(
                matched(null, new Candidate("p1", "이치란", "오사카", 34.70, 135.50), PlaceCategory.FOOD, false));
        when(repository.findFirstByTripIdAndPlaceId(7L, "p1"))
                .thenReturn(Optional.empty())                 // 구글 부르기 전
                .thenReturn(Optional.of(existing));           // 저장 직전

        service.add(1L, 7L, "p1");

        assertThat(existing.getListNames()).containsExactlyInAnyOrder("오사카 맛집", "직접 추가");
        verify(repository, never()).save(any());
    }

    private SavedPlace place(Long id, String name, String listName) {
        return place(id, name, listName, null);
    }

    private SavedPlace place(Long id, String name, String listName, String note) {
        SavedPlace p = SavedPlace.fromTakeout(trip, null, name, note, null, null, null, listName);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private void pending(SavedPlace... places) {
        when(repository.findAllByTripIdAndMatchStatus(7L, PlaceMatchStatus.PENDING)).thenReturn(List.of(places));
        for (SavedPlace p : places) when(repository.findById(p.getId())).thenReturn(Optional.of(p));
    }

    // 장소 1개씩 매칭을 부르므로, 들어온 장소 id 로 결과를 골라 돌려준다
    private void googleReturns(Map<Long, MatchResult> byId) {
        when(placeMatcher.matchPlaces(any(), anyDouble(), anyDouble())).thenAnswer(inv -> {
            List<PlaceInput> inputs = inv.getArgument(0);
            return List.of(byId.get(inputs.get(0).id()));
        });
    }

    private static MatchResult matched(Long id, Candidate c, PlaceCategory category, boolean closed) {
        return new MatchResult(id, MatchStatus.MATCHED, c, List.of(), category, closed);
    }
}
