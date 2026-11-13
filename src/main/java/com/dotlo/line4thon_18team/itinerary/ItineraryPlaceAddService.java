package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.itinerary.ItineraryValidationService.ValidationRun;
import com.dotlo.line4thon_18team.itinerary.edit.ItineraryDiff;
import com.dotlo.line4thon_18team.itinerary.edit.dto.ItineraryPlaceAddResult;
import com.dotlo.line4thon_18team.itinerary.edit.dto.ItineraryPlaceAddSuggestion;
import com.dotlo.line4thon_18team.itinerary.validation.TravelEstimator;
import com.dotlo.line4thon_18team.itinerary.validation.model.GeoPoint;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItineraryVersionView;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
import com.dotlo.line4thon_18team.place.entity.PlaceMatchStatus;
import com.dotlo.line4thon_18team.place.entity.SavedPlace;
import com.dotlo.line4thon_18team.place.repository.SavedPlaceRepository;
import com.dotlo.line4thon_18team.place.service.PlaceEnrichService;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 장소 하나를 일정에 넣기 (COLLAB-ADD-001). 투표에서 1위를 한 장소나 사용자가 고른 장소를 일정에 넣는다.
 *
 * <p>화면은 두 단계다. 먼저 {@link #suggest}로 "언제 넣을까요?"에 쓸 날짜 목록과 추천 날을 받고,
 * 사용자가 날을 고르면 {@link #add}로 그 날에 넣는다. 날을 비우고 부르면 AI가 날까지 직접 고른다.
 *
 * <p>어느 순서에 넣을지는 텍스트 수정({@link ItineraryEditService})이 정한다. 수정 쪽이 이미
 * "지금 일정 + 넣을 수 있는 장소 목록"을 넘기고 자리를 정하게 되어 있어서, 요청 문장만 서버가 만든다.
 *
 * <p>미리보기를 거치지 않고 바로 저장한다. 마음에 들지 않으면 되돌리기로 한 번에 되돌릴 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ItineraryPlaceAddService {

    private final PlaceEnrichService placeEnrichService;
    private final SavedPlaceRepository savedPlaceRepository;
    private final ItineraryService itineraryService;
    private final ItineraryEditService editService;
    private final ItineraryValidationService validationService;

    /**
     * 어느 날에 넣을지 고르는 화면에 쓸 값. 일정은 바꾸지 않지만, 좌표가 있어야 동선을 재므로
     * 저장 장소에 담는 것까지는 여기서 한다.
     *
     * @throws BusinessException 구성원이 아닌 여행·없는 여행(404), 없는 placeId(400),
     *                           저장된 일정이 없음(404), 폐업·도시 밖 장소(400), 이미 일정에 있는 장소(400)
     */
    public ItineraryPlaceAddSuggestion suggest(long tripId, long userId, String placeId) {
        SavedPlace place = prepare(tripId, userId, placeId);
        ItineraryVersionView current = itineraryService.getLatest(tripId, userId);
        requireNotInItinerary(current.draft(), placeId);

        ValidationRun run = validationService.prepare(tripId, userId);
        Trip trip = run.trip();
        Map<String, GeoPoint> points = pointsByPlaceId(run.places());
        Map<LocalDate, List<VisitDraft>> byDate = visitsByDate(current.draft());
        GeoPoint target = points.get(placeId);

        List<ItineraryPlaceAddSuggestion.Day> days = new ArrayList<>();
        Integer recommended = null;
        double best = Double.MAX_VALUE;
        for (int dayNumber = 1; dayNumber <= dayCount(trip); dayNumber++) {
            LocalDate date = trip.getStartDate().plusDays(dayNumber - 1L);
            List<VisitDraft> visits = byDate.getOrDefault(date, List.of());
            days.add(new ItineraryPlaceAddSuggestion.Day(dayNumber, date, visits.size(), false));
            if (target == null) {
                continue;
            }
            // 그 날 방문들의 한가운데에서 잰다. 방문이 없는 날은 그 날 출발지(전날 밤 숙소, 없으면 도시 중심)
            GeoPoint anchor = centroid(visits, points);
            double meters = TravelEstimator.straightLineMeters(
                    anchor == null ? run.context().startOf(date) : anchor, target);
            if (meters < best) {
                best = meters;
                recommended = dayNumber;
            }
        }
        if (recommended != null) {
            int mark = recommended;
            days.replaceAll(day -> day.day() == mark
                    ? new ItineraryPlaceAddSuggestion.Day(day.day(), day.date(), day.visitCount(), true)
                    : day);
        }
        return new ItineraryPlaceAddSuggestion(placeId, place.getName(), recommended, List.copyOf(days));
    }

    /**
     * @param day 사용자가 고른 날 (1일차부터). null 이면 AI 가 날까지 고른다
     * @throws BusinessException 구성원이 아닌 여행·없는 여행(404), 없는 placeId(400),
     *                           저장된 일정이 없음(404), 폐업·도시 밖 장소(400), 이미 일정에 있는 장소(400),
     *                           여행 기간 밖 날(400), 넣을 자리를 못 찾음(400)
     */
    public ItineraryPlaceAddResult add(long tripId, long userId, String placeId, Integer day, String note) {
        SavedPlace place = prepare(tripId, userId, placeId);
        ItineraryVersionView current = itineraryService.getLatest(tripId, userId);
        requireNotInItinerary(current.draft(), placeId);

        LocalDate date = day == null ? null : dateOf(tripId, userId, day);
        EditedItinerary edited = editService.edit(tripId, userId, current.draft(),
                request(place.getName(), day, note));

        if (!contains(edited.draft(), placeId, date)) {
            // AI가 넣을 자리를 못 찾았거나 고른 날이 아닌 곳에 넣은 경우. 똑같은 일정을 새 버전으로 쌓지 않는다
            throw new BusinessException(ErrorCode.PLACE_ADD_FAILED,
                    ErrorCode.PLACE_ADD_FAILED.getMessage()
                            + " 장소는 저장 장소 목록에 담아 두었으니 직접 넣으실 수 있습니다. " + edited.reason());
        }

        ItineraryDiff diff = ItineraryDiff.between(current.draft(), edited.draft());
        ItineraryVersionView saved = itineraryService.save(tripId, userId, ItinerarySource.AI_EDITED,
                current.version(), edited.draft(), edited.validation());
        return new ItineraryPlaceAddResult(place.getName(), edited.reason(), saved, diff);
    }

    /** 저장 장소에 담고, 일정에 넣을 수 있는 상태인지 본다. */
    private SavedPlace prepare(long tripId, long userId, String placeId) {
        // 저장 장소에 없으면 BE2가 구글에서 받아 담는다. 이미 있으면 리스트 이름만 붙고 구글을 부르지 않는다.
        // 트랜잭션 밖에서 불러야 한다. add 는 여행 행을 잠근 채 구글 Details 를 부르기 때문에,
        // 바깥에 트랜잭션이 있으면 구글을 기다리는 동안 같은 여행의 장소 추가·보강이 모두 줄을 선다
        placeEnrichService.add(userId, tripId, placeId);

        SavedPlace place = savedPlaceRepository.findFirstByTripIdAndPlaceId(tripId, placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PLACE_ID));
        if (place.getMatchStatus() != PlaceMatchStatus.MATCHED) {
            // 사용자가 지목한 장소라서 조용히 빼지 않고 왜 못 넣는지 알려준다
            throw new BusinessException(ErrorCode.PLACE_NOT_PLANNABLE,
                    ErrorCode.PLACE_NOT_PLANNABLE.getMessage() + " " + describe(place.getMatchStatus()));
        }
        return place;
    }

    private LocalDate dateOf(long tripId, long userId, int day) {
        Trip trip = validationService.prepare(tripId, userId).trip();
        if (day > dayCount(trip)) {
            throw new BusinessException(ErrorCode.INVALID_ITINERARY,
                    "여행은 " + dayCount(trip) + "일이라 " + day + "일차에 넣을 수 없습니다.");
        }
        return trip.getStartDate().plusDays(day - 1L);
    }

    /** 장소 이름과 고른 날, 사용자 요청을 수정 요청 문장으로 만든다. 태그 제거는 PlanEditTask 가 한다. */
    private static String request(String name, Integer day, String note) {
        StringBuilder request = new StringBuilder("일정에 '").append(name).append("' 한 곳을 ");
        if (day == null) {
            request.append("알맞은 날과 순서에 넣어 주세요.");
        } else {
            request.append(day).append("일차 안에서 알맞은 순서에 넣어 주세요. 다른 날로 보내지 마세요.");
        }
        request.append(" 넣는 김에 그날 동선이 자연스러워지도록 순서를 다시 잡아도 됩니다.");
        if (note != null && !note.isBlank()) {
            request.append(' ').append(note.strip());
        }
        return request.toString();
    }

    private static int dayCount(Trip trip) {
        return (int) ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
    }

    private static Map<String, GeoPoint> pointsByPlaceId(List<SavedPlace> places) {
        Map<String, GeoPoint> points = new LinkedHashMap<>();
        for (SavedPlace place : places) {
            if (place.getPlaceId() != null && place.getLatitude() != null && place.getLongitude() != null) {
                points.putIfAbsent(place.getPlaceId(), new GeoPoint(place.getLatitude(), place.getLongitude()));
            }
        }
        return points;
    }

    private static Map<LocalDate, List<VisitDraft>> visitsByDate(ItineraryDraft draft) {
        Map<LocalDate, List<VisitDraft>> byDate = new LinkedHashMap<>();
        draft.days().forEach(day -> byDate.put(day.date(), day.visits()));
        return byDate;
    }

    /** 그 날 방문들의 한가운데. 좌표를 아는 방문이 하나도 없으면 null. */
    private static GeoPoint centroid(List<VisitDraft> visits, Map<String, GeoPoint> points) {
        double lat = 0;
        double lng = 0;
        int count = 0;
        for (VisitDraft visit : visits) {
            GeoPoint point = points.get(visit.placeId());
            if (point != null) {
                lat += point.lat();
                lng += point.lng();
                count++;
            }
        }
        return count == 0 ? null : new GeoPoint(lat / count, lng / count);
    }

    private static void requireNotInItinerary(ItineraryDraft draft, String placeId) {
        if (contains(draft, placeId, null)) {
            throw new BusinessException(ErrorCode.PLACE_ALREADY_IN_ITINERARY);
        }
    }

    /** date 가 null 이면 어느 날이든, 아니면 그 날에 있는지 본다. */
    private static boolean contains(ItineraryDraft draft, String placeId, LocalDate date) {
        return draft.days().stream()
                .filter(day -> date == null || date.equals(day.date()))
                .flatMap(day -> day.visits().stream())
                .anyMatch(visit -> placeId.equals(visit.placeId()));
    }

    private static String describe(PlaceMatchStatus status) {
        return switch (status) {
            case CLOSED -> "폐업한 장소입니다.";
            case OUT_OF_CITY -> "여행 도시에서 50km 넘게 떨어진 장소입니다.";
            default -> "아직 확인이 끝나지 않은 장소입니다. 저장 장소 목록에서 먼저 확인해 주세요.";
        };
    }
}
