package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.itinerary.edit.entity.ItineraryEditRequest;
import com.dotlo.line4thon_18team.itinerary.edit.repository.ItineraryEditRequestRepository;
import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.port.TravelTimeProvider;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;
import com.dotlo.line4thon_18team.itinerary.version.repository.ItineraryVersionRepository;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
import com.dotlo.line4thon_18team.place.OpeningHours;
import com.dotlo.line4thon_18team.preference.PreferenceRepository;
import com.dotlo.line4thon_18team.preference.StoredPreference;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여행 삭제용 BE3 데이터 정리. MySQL이 필요하다 (docker compose up -d).
 * 정리가 자기 트랜잭션을 따로 열어서 테스트 트랜잭션으로 되돌릴 수 없으므로, 만든 데이터는 테스트 끝에 지운다.
 */
@SpringBootTest
class ItineraryTripCleanupServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 28);

    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired ItineraryVersionRepository itineraryVersions;
    @Autowired ItineraryEditRequestRepository editRequests;
    @Autowired PreferenceRepository preferences;
    @Autowired ItineraryService itineraryService;
    @Autowired ItineraryTripCleanupService cleanupService;

    @MockitoBean OpeningHours openingHours;
    @MockitoBean TravelTimeProvider travelTimeProvider;

    private User owner;
    private Trip target;
    private Trip other;

    @BeforeEach
    void setUp() {
        owner = users.save(newUser());
        target = trips.save(Trip.create(owner, "오사카", START, START.plusDays(1), Companion.ALONE, null));
        other = trips.save(Trip.create(owner, "교토", START, START.plusDays(1), Companion.ALONE, null));
        fill(target);
        fill(other);
    }

    @AfterEach
    void tearDown() {
        cleanupService.deleteByTrip(target.getId());
        cleanupService.deleteByTrip(other.getId());
        trips.delete(target);
        trips.delete(other);
        users.delete(owner);
    }

    @Test
    void 그_여행의_일정_수정안_취향을_모두_지운다() {
        cleanupService.deleteByTrip(target.getId());

        assertThat(editRequests.findFirstByTripIdAndBaseVersionOrderByIdDesc(target.getId(), 1)).isEmpty();
        assertThat(itineraryVersions.findByTripIdOrderByVersionDesc(target.getId())).isEmpty();
        assertThat(preferences.findAllByTrip(target.getId())).isEmpty();
    }

    @Test
    void 다른_여행의_데이터는_건드리지_않는다() {
        cleanupService.deleteByTrip(target.getId());

        assertThat(editRequests.findFirstByTripIdAndBaseVersionOrderByIdDesc(other.getId(), 1)).isPresent();
        assertThat(itineraryVersions.findByTripIdOrderByVersionDesc(other.getId())).hasSize(1);
        assertThat(preferences.findAllByTrip(other.getId())).hasSize(1);
    }

    @Test
    void 지울_것이_없어도_그냥_끝난다() {
        cleanupService.deleteByTrip(target.getId());

        cleanupService.deleteByTrip(target.getId());   // 두 번째 호출
        assertThat(itineraryVersions.findByTripIdOrderByVersionDesc(target.getId())).isEmpty();
    }

    /** 일정 버전 1개 + 수정안 1개 + 취향 1개 */
    private void fill(Trip trip) {
        ItineraryDraft draft = ItineraryDraft.of(DayDraft.of(START, VisitDraft.of("v-aaa", "ChIJ-a")));
        ValidationResult validation = ValidationResult.of(List.of(), List.of());
        itineraryService.save(trip.getId(), owner.getId(), ItinerarySource.AI_GENERATED, null, draft, validation);
        editRequests.save(ItineraryEditRequest.create(trip, 1, "둘째 날 하나 빼 주세요", "빼 드렸습니다.",
                owner.getId(), "{}", "{}"));
        preferences.save(new StoredPreference(trip.getId(), owner.getId(), PreferenceProfile.defaults(),
                null, Instant.parse("2026-11-28T00:00:00Z")));
    }

    private static User newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return User.builder().googleSub("sub-clean-" + id).email(id + "@test.com").name("정리테스트").build();
    }
}
