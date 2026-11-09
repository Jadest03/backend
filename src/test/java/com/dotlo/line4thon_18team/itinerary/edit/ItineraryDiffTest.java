package com.dotlo.line4thon_18team.itinerary.edit;

import com.dotlo.line4thon_18team.itinerary.edit.ItineraryDiff.ChangeKind;
import com.dotlo.line4thon_18team.itinerary.edit.ItineraryDiff.ItemChange;
import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 일정 두 벌의 변경점. itemId 로 같은 방문을 찾는다. */
class ItineraryDiffTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 12, 1);
    private static final LocalDate DAY2 = LocalDate.of(2026, 12, 2);

    @Test
    void 같은_일정이면_변경점이_없다() {
        ItineraryDraft draft = ItineraryDraft.of(DayDraft.of(DAY1, visit("v-1"), visit("v-2")));

        assertThat(ItineraryDiff.between(draft, draft).isEmpty()).isTrue();
    }

    @Test
    void 후_일정에만_있으면_추가다() {
        ItineraryDraft before = ItineraryDraft.of(DayDraft.of(DAY1, visit("v-1")));
        ItineraryDraft after = ItineraryDraft.of(DayDraft.of(DAY1, visit("v-1"), visit("v-2")));

        ItemChange added = only(ItineraryDiff.between(before, after), ChangeKind.ADDED);
        assertThat(added.itemId()).isEqualTo("v-2");
        assertThat(added.fromDate()).isNull();
        assertThat(added.toDate()).isEqualTo(DAY1);
    }

    @Test
    void 전_일정에만_있으면_빠진_것이다() {
        ItineraryDraft before = ItineraryDraft.of(DayDraft.of(DAY1, visit("v-1"), visit("v-2")));
        ItineraryDraft after = ItineraryDraft.of(DayDraft.of(DAY1, visit("v-1")));

        ItemChange removed = only(ItineraryDiff.between(before, after), ChangeKind.REMOVED);
        assertThat(removed.itemId()).isEqualTo("v-2");
        assertThat(removed.fromDate()).isEqualTo(DAY1);
        assertThat(removed.toDate()).isNull();
    }

    @Test
    void 같은_방문이_다른_날에_있으면_날짜_이동이다() {
        ItineraryDraft before = ItineraryDraft.of(
                DayDraft.of(DAY1, visit("v-1"), visit("v-2")), DayDraft.of(DAY2));
        ItineraryDraft after = ItineraryDraft.of(
                DayDraft.of(DAY1, visit("v-1")), DayDraft.of(DAY2, visit("v-2")));

        ItemChange moved = only(ItineraryDiff.between(before, after), ChangeKind.MOVED_DAY);
        assertThat(moved.itemId()).isEqualTo("v-2");
        assertThat(moved.fromDate()).isEqualTo(DAY1);
        assertThat(moved.toDate()).isEqualTo(DAY2);
    }

    @Test
    void 체류시간이_바뀌면_알려준다() {
        ItineraryDraft before = ItineraryDraft.of(DayDraft.of(DAY1, visit("v-1")));
        ItineraryDraft after = ItineraryDraft.of(DayDraft.of(DAY1, visit("v-1").withStayMinutes(90)));

        ItemChange changed = only(ItineraryDiff.between(before, after), ChangeKind.STAY_CHANGED);
        assertThat(changed.fromStayMinutes()).isNull();
        assertThat(changed.toStayMinutes()).isEqualTo(90);
    }

    @Test
    void 날짜와_체류시간이_같이_바뀌면_두_건이다() {
        ItineraryDraft before = ItineraryDraft.of(DayDraft.of(DAY1, visit("v-1")), DayDraft.of(DAY2));
        ItineraryDraft after = ItineraryDraft.of(
                DayDraft.of(DAY1), DayDraft.of(DAY2, visit("v-1").withStayMinutes(60)));

        ItineraryDiff diff = ItineraryDiff.between(before, after);
        assertThat(diff.changes()).hasSize(2);
        assertThat(diff.changes()).extracting(ItemChange::kind)
                .containsExactly(ChangeKind.MOVED_DAY, ChangeKind.STAY_CHANGED);
    }

    @Test
    void 방문을_지워도_남은_방문이_순서_변경으로_잡히지_않는다() {
        // [a, b, c, d] 에서 a 를 지우면 b·c·d 번호가 하나씩 밀린다. 상대 순서는 그대로다.
        ItineraryDraft before = ItineraryDraft.of(
                DayDraft.of(DAY1, visit("v-a"), visit("v-b"), visit("v-c"), visit("v-d")));
        ItineraryDraft after = ItineraryDraft.of(
                DayDraft.of(DAY1, visit("v-b"), visit("v-c"), visit("v-d")));

        ItineraryDiff diff = ItineraryDiff.between(before, after);
        assertThat(diff.reorderedDays()).isEmpty();
        assertThat(diff.changesOf(ChangeKind.REMOVED)).hasSize(1);
    }

    @Test
    void 방문을_추가해도_순서_변경으로_잡히지_않는다() {
        ItineraryDraft before = ItineraryDraft.of(DayDraft.of(DAY1, visit("v-a"), visit("v-b")));
        ItineraryDraft after = ItineraryDraft.of(
                DayDraft.of(DAY1, visit("v-new"), visit("v-a"), visit("v-b")));

        assertThat(ItineraryDiff.between(before, after).reorderedDays()).isEmpty();
    }

    @Test
    void 같은_날에서_순서가_바뀌면_그_날을_알려준다() {
        ItineraryDraft before = ItineraryDraft.of(
                DayDraft.of(DAY1, visit("v-a"), visit("v-b")), DayDraft.of(DAY2, visit("v-c")));
        ItineraryDraft after = ItineraryDraft.of(
                DayDraft.of(DAY1, visit("v-b"), visit("v-a")), DayDraft.of(DAY2, visit("v-c")));

        ItineraryDiff diff = ItineraryDiff.between(before, after);
        assertThat(diff.reorderedDays()).containsExactly(DAY1);
        assertThat(diff.changes()).isEmpty();
    }

    @Test
    void 다른_날로_옮긴_방문은_남은_날의_순서_판정에서_빠진다() {
        // 1일차 b 를 2일차로 옮겼을 뿐이고, 1일차에 남은 a·c 순서는 그대로다
        ItineraryDraft before = ItineraryDraft.of(
                DayDraft.of(DAY1, visit("v-a"), visit("v-b"), visit("v-c")), DayDraft.of(DAY2));
        ItineraryDraft after = ItineraryDraft.of(
                DayDraft.of(DAY1, visit("v-a"), visit("v-c")), DayDraft.of(DAY2, visit("v-b")));

        ItineraryDiff diff = ItineraryDiff.between(before, after);
        assertThat(diff.reorderedDays()).isEmpty();
        assertThat(diff.changesOf(ChangeKind.MOVED_DAY)).hasSize(1);
    }

    @Test
    void 예약_시각이_바뀌면_알려준다() {
        ItineraryDraft before = ItineraryDraft.of(
                DayDraft.of(DAY1, visit("v-1").withFixedStart(LocalTime.of(12, 0))));
        ItineraryDraft after = ItineraryDraft.of(
                DayDraft.of(DAY1, visit("v-1").withFixedStart(LocalTime.of(18, 0))));

        ItemChange changed = only(ItineraryDiff.between(before, after), ChangeKind.FIXED_START_CHANGED);
        assertThat(changed.fromFixedStart()).isEqualTo(LocalTime.of(12, 0));
        assertThat(changed.toFixedStart()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void 예약_시각을_지우면_알려준다() {
        ItineraryDraft before = ItineraryDraft.of(
                DayDraft.of(DAY1, visit("v-1").withFixedStart(LocalTime.of(12, 0))));
        ItineraryDraft after = ItineraryDraft.of(DayDraft.of(DAY1, visit("v-1")));

        ItineraryDiff diff = ItineraryDiff.between(before, after);
        assertThat(diff.isEmpty()).isFalse();
        ItemChange changed = only(diff, ChangeKind.FIXED_START_CHANGED);
        assertThat(changed.fromFixedStart()).isEqualTo(LocalTime.of(12, 0));
        assertThat(changed.toFixedStart()).isNull();
    }

    private static VisitDraft visit(String itemId) {
        return VisitDraft.of(itemId, "place-" + itemId);
    }

    private static ItemChange only(ItineraryDiff diff, ChangeKind kind) {
        assertThat(diff.changes()).hasSize(1);
        return diff.changesOf(kind).get(0);
    }
}
