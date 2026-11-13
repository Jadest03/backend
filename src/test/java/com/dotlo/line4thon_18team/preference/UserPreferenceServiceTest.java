package com.dotlo.line4thon_18team.preference;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.place.PlaceCategory;
import com.dotlo.line4thon_18team.preference.domain.DayStartPreference;
import com.dotlo.line4thon_18team.preference.domain.PreferenceChip;
import com.dotlo.line4thon_18team.preference.domain.PreferenceProfile;
import com.dotlo.line4thon_18team.preference.domain.TravelPace;
import com.dotlo.line4thon_18team.preference.domain.WalkingPreference;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 사용자 기본 취향 (가입할 때 고르는 취향). 값 검사는 여행별 취향과 같은 규칙을 쓴다. */
class UserPreferenceServiceTest {

    private static final long USER = 7L;
    private static final Instant NOW = Instant.parse("2026-11-11T01:00:00Z");

    private final UserPreferenceService service = new UserPreferenceService(
            new InMemoryUserPreferenceRepository(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 저장하면_칩과_함께_돌려준다() {
        UserPreferenceView view = service.save(USER,
                input(TravelPace.RELAXED, List.of(PlaceCategory.CAFE), List.of(), List.of(), "천천히 다니고 싶어요"));

        assertThat(view.userId()).isEqualTo(USER);
        assertThat(view.saved()).isTrue();
        assertThat(view.preferences().pace()).isEqualTo(TravelPace.RELAXED);
        assertThat(view.preferences().interests()).containsExactly(PlaceCategory.CAFE);
        assertThat(view.rawText()).isEqualTo("천천히 다니고 싶어요");
        assertThat(view.updatedAt()).isEqualTo(NOW);
        assertThat(view.chips()).extracting(PreferenceChip::label).isNotEmpty();
    }

    @Test
    void 고른_적이_없으면_기본값을_돌려준다() {
        UserPreferenceView view = service.get(USER);

        assertThat(view.saved()).isFalse();
        assertThat(view.preferences()).isEqualTo(PreferenceProfile.defaults());
        assertThat(view.rawText()).isNull();
        assertThat(view.updatedAt()).isNull();
    }

    @Test
    void 일정_생성에_쓸_취향은_고른_적이_없으면_비어_있다() {
        assertThat(service.profile(USER)).isEmpty();

        service.save(USER, input(TravelPace.PACKED, List.of(), List.of(), List.of(), null));

        assertThat(service.profile(USER)).map(PreferenceProfile::pace).contains(TravelPace.PACKED);
    }

    @Test
    void 다시_저장하면_덮어쓴다() {
        service.save(USER, input(TravelPace.PACKED, List.of(PlaceCategory.FOOD), List.of(), List.of(), "많이 보고 싶어요"));
        service.save(USER, input(TravelPace.RELAXED, List.of(), List.of(), List.of(), null));

        UserPreferenceView view = service.get(USER);
        assertThat(view.preferences().pace()).isEqualTo(TravelPace.RELAXED);
        assertThat(view.preferences().interests()).isEmpty();
        assertThat(view.rawText()).isNull();
    }

    @Test
    void 사용자별로_따로_저장된다() {
        service.save(USER, input(TravelPace.PACKED, List.of(), List.of(), List.of(), null));

        assertThat(service.get(8L).saved()).isFalse();
    }

    @Test
    void 취향_원문이_500자를_넘으면_400이다() {
        String tooLong = "가".repeat(501);

        assertThatThrownBy(() -> service.save(USER, input(TravelPace.NORMAL, List.of(), List.of(), List.of(), tooLong)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PREFERENCE_TEXT_TOO_LONG);
    }

    @Test
    void 메모가_5개를_넘으면_400이고_위반_내용을_알려준다() {
        List<String> notes = List.of("1", "2", "3", "4", "5", "6");

        assertThatThrownBy(() -> service.save(USER, input(TravelPace.NORMAL, List.of(), List.of(), notes, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PREFERENCE)
                .hasMessageContaining("notes는 최대 5개입니다");
    }

    @Test
    void 바디가_없으면_400이다() {
        assertThatThrownBy(() -> service.save(USER, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST_BODY);
    }

    private static PreferenceInput input(TravelPace pace, List<PlaceCategory> interests, List<PlaceCategory> avoid,
                                         List<String> notes, String rawText) {
        return new PreferenceInput(pace, WalkingPreference.NORMAL, DayStartPreference.NORMAL,
                interests, avoid, notes, rawText);
    }
}
