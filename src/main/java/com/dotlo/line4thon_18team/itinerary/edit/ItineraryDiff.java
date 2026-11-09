package com.dotlo.line4thon_18team.itinerary.edit;

import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 일정 두 벌의 변경점 (EDIT-DIFF-001). 같은 방문은 itemId로 찾는다.
 *
 * <p>순서 변경은 날짜 단위로만 알려준다. 방문 하나를 지우면 뒤 방문의 번호가 전부 밀려서, 번호를
 * 그대로 비교하면 손대지 않은 방문까지 "옮겨졌다"고 나온다. 그래서 양쪽에 다 있고 날짜도 그대로인
 * 방문만 추려 그 날의 상대 순서를 비교한다. 어느 방문이 옮겨졌는지까지 가리려면 최장 증가 부분
 * 수열이 필요한데, 하루 방문이 열 개를 넘지 않아 그만한 값이 없다.
 *
 * @param changes       방문별 변경. 전 일정 순서로 빠짐·날짜 이동·체류시간·예약 시각, 그다음 후 일정 순서로 추가
 * @param reorderedDays 그 날 안에서 순서가 바뀐 날짜
 */
public record ItineraryDiff(List<ItemChange> changes, Set<LocalDate> reorderedDays) {

    public ItineraryDiff {
        changes = changes == null ? List.of() : List.copyOf(changes);
        reorderedDays = reorderedDays == null ? Set.of() : Set.copyOf(reorderedDays);
    }

    public enum ChangeKind {
        /** 후 일정에만 있는 방문 */
        ADDED,
        /** 전 일정에만 있는 방문 */
        REMOVED,
        /** 같은 방문이 다른 날로 옮겨짐 */
        MOVED_DAY,
        /** 체류시간이 바뀜 */
        STAY_CHANGED,
        /** 예약 등으로 정해둔 시작 시각이 바뀜 (지우거나 새로 넣은 경우도) */
        FIXED_START_CHANGED
    }

    /**
     * 방문 한 건의 변경. 한 방문에서 날짜·체류시간·예약 시각이 같이 바뀌면 그만큼 여러 건으로 들어간다.
     *
     * @param fromDate 전 날짜. ADDED 는 null
     * @param toDate   후 날짜. REMOVED 는 null
     */
    public record ItemChange(ChangeKind kind, String itemId, String placeId,
                             LocalDate fromDate, LocalDate toDate,
                             Integer fromStayMinutes, Integer toStayMinutes,
                             LocalTime fromFixedStart, LocalTime toFixedStart) {
    }

    public boolean isEmpty() {
        return changes.isEmpty() && reorderedDays.isEmpty();
    }

    public List<ItemChange> changesOf(ChangeKind kind) {
        return changes.stream().filter(change -> change.kind() == kind).toList();
    }

    public static ItineraryDiff between(ItineraryDraft before, ItineraryDraft after) {
        Map<String, Placed> olds = index(before);
        Map<String, Placed> news = index(after);
        List<ItemChange> changes = new ArrayList<>();

        for (Placed old : olds.values()) {
            Placed now = news.get(old.itemId());
            if (now == null) {
                changes.add(change(ChangeKind.REMOVED, old, null));
                continue;
            }
            if (!old.date().equals(now.date())) {
                changes.add(change(ChangeKind.MOVED_DAY, old, now));
            }
            if (!Objects.equals(old.stayMinutes(), now.stayMinutes())) {
                changes.add(change(ChangeKind.STAY_CHANGED, old, now));
            }
            if (!Objects.equals(old.fixedStart(), now.fixedStart())) {
                changes.add(change(ChangeKind.FIXED_START_CHANGED, old, now));
            }
        }
        for (Placed now : news.values()) {
            if (!olds.containsKey(now.itemId())) {
                changes.add(change(ChangeKind.ADDED, null, now));
            }
        }
        return new ItineraryDiff(changes, reorderedDays(before, after, olds, news));
    }

    private static ItemChange change(ChangeKind kind, Placed before, Placed after) {
        Placed any = before != null ? before : after;
        return new ItemChange(kind, any.itemId(), any.placeId(),
                before == null ? null : before.date(),
                after == null ? null : after.date(),
                before == null ? null : before.stayMinutes(),
                after == null ? null : after.stayMinutes(),
                before == null ? null : before.fixedStart(),
                after == null ? null : after.fixedStart());
    }

    private static Set<LocalDate> reorderedDays(ItineraryDraft before, ItineraryDraft after,
                                                Map<String, Placed> olds, Map<String, Placed> news) {
        Map<LocalDate, List<String>> oldOrder = stayedOrder(before, news);
        Map<LocalDate, List<String>> newOrder = stayedOrder(after, olds);
        Set<LocalDate> reordered = new LinkedHashSet<>();
        oldOrder.forEach((date, ids) -> {
            if (!ids.equals(newOrder.getOrDefault(date, List.of()))) {
                reordered.add(date);
            }
        });
        return reordered;
    }

    /** 날짜별로, 상대편 일정에도 같은 날에 있는 방문만 순서대로 추린다. */
    private static Map<LocalDate, List<String>> stayedOrder(ItineraryDraft draft, Map<String, Placed> other) {
        Map<LocalDate, List<String>> byDate = new LinkedHashMap<>();
        for (DayDraft day : draft.days()) {
            List<String> ids = new ArrayList<>();
            for (VisitDraft visit : day.visits()) {
                Placed counterpart = other.get(visit.itemId());
                if (counterpart != null && counterpart.date().equals(day.date())) {
                    ids.add(visit.itemId());
                }
            }
            byDate.put(day.date(), ids);
        }
        return byDate;
    }

    // itemId 는 일정 안에서 유일하다 (검증기가 중복을 거른다)
    private static Map<String, Placed> index(ItineraryDraft draft) {
        Map<String, Placed> byItemId = new LinkedHashMap<>();
        for (DayDraft day : draft.days()) {
            for (VisitDraft visit : day.visits()) {
                byItemId.put(visit.itemId(), new Placed(visit.itemId(), visit.placeId(), day.date(),
                        visit.stayMinutes(), visit.fixedStart()));
            }
        }
        return byItemId;
    }

    private record Placed(String itemId, String placeId, LocalDate date, Integer stayMinutes, LocalTime fixedStart) {
    }
}
