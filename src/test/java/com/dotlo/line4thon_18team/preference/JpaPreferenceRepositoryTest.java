package com.dotlo.line4thon_18team.preference;

import com.dotlo.line4thon_18team.place.PlaceCategory;
import com.dotlo.line4thon_18team.preference.domain.DayStartPreference;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;
import com.dotlo.line4thon_18team.preference.domain.TravelPace;
import com.dotlo.line4thon_18team.preference.domain.WalkingPreference;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// MySQL이 필요하다 (docker compose up -d). 테스트가 끝나면 롤백된다.
@SpringBootTest
@Transactional
class JpaPreferenceRepositoryTest {

    private static final long TRIP = 900_001L;
    private static final Instant T1 = Instant.parse("2026-11-03T06:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-03T07:00:00Z");

    @Autowired PreferenceRepository repository;
    @Autowired PreferenceEntityRepository entities;
    @Autowired EntityManager em;

    @Test
    void 저장한_취향을_DB에서_그대로_읽는다() {
        PreferenceProfile profile = new PreferenceProfile(TravelPace.RELAXED, WalkingPreference.LESS,
                DayStartPreference.LATE, List.of(PlaceCategory.CAFE, PlaceCategory.FOOD),
                List.of(PlaceCategory.SHOPPING), List.of("해산물, 갑각류는 빼 주세요"));
        StoredPreference saved = new StoredPreference(TRIP, 7L, profile, "여유롭게 카페 위주로", T1);

        repository.save(saved);
        em.flush();
        em.clear();

        assertThat(repository.findByTripAndUser(TRIP, 7L)).contains(saved);
    }

    @Test
    void 같은_여행과_사용자로_다시_저장하면_덮어쓴다() {
        repository.save(new StoredPreference(TRIP, 7L, PreferenceProfile.defaults(), null, T1));
        PreferenceProfile changed = new PreferenceProfile(TravelPace.PACKED, WalkingPreference.MORE,
                DayStartPreference.EARLY, List.of(PlaceCategory.NATURE), List.of(), List.of());
        repository.save(new StoredPreference(TRIP, 7L, changed, "알차게", T2));
        em.flush();
        em.clear();

        List<StoredPreference> all = repository.findAllByTrip(TRIP);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).profile()).isEqualTo(changed);
        assertThat(all.get(0).updatedAt()).isEqualTo(T2);
        assertThat(entities.findAllByTripIdOrderByUserIdAsc(TRIP)).hasSize(1);
    }

    @Test
    void 여행의_구성원_취향을_사용자_순서로_돌려준다() {
        repository.save(new StoredPreference(TRIP, 9L, PreferenceProfile.defaults(), null, T1));
        repository.save(new StoredPreference(TRIP, 3L, PreferenceProfile.defaults(), null, T1));
        repository.save(new StoredPreference(TRIP + 1, 5L, PreferenceProfile.defaults(), null, T1));
        em.flush();
        em.clear();

        assertThat(repository.findAllByTrip(TRIP)).extracting(StoredPreference::userId).containsExactly(3L, 9L);
    }

    @Test
    void 탈퇴한_사용자의_취향만_여행마다_지운다() {
        repository.save(new StoredPreference(TRIP, 7L, PreferenceProfile.defaults(), "내가 쓴 문장", T1));
        repository.save(new StoredPreference(TRIP + 1, 7L, PreferenceProfile.defaults(), null, T1));
        repository.save(new StoredPreference(TRIP, 8L, PreferenceProfile.defaults(), null, T1));
        em.flush();

        repository.deleteByUser(7L);
        em.flush();
        em.clear();

        assertThat(repository.findByTripAndUser(TRIP, 7L)).isEmpty();
        assertThat(repository.findByTripAndUser(TRIP + 1, 7L)).isEmpty();
        assertThat(repository.findByTripAndUser(TRIP, 8L)).isPresent();   // 남은 사람 것은 그대로
    }

    @Test
    void 저장한_적이_없으면_빈_값이다() {
        assertThat(repository.findByTripAndUser(TRIP, 404L)).isEmpty();
    }
}
