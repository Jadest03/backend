package com.dotlo.line4thon_18team.chat.poll;

import com.dotlo.line4thon_18team.chat.poll.ai.PollPickInput;
import com.dotlo.line4thon_18team.chat.poll.ai.PollPickOutput;
import com.dotlo.line4thon_18team.chat.poll.ai.PollPickTask;
import com.dotlo.line4thon_18team.chat.poll.ai.PollSearchInput;
import com.dotlo.line4thon_18team.chat.poll.ai.PollSearchOutput;
import com.dotlo.line4thon_18team.chat.poll.ai.PollSearchTask;
import com.dotlo.line4thon_18team.chat.poll.dto.NewCandidate;
import com.dotlo.line4thon_18team.chat.poll.dto.PollCandidateCommand;
import com.dotlo.line4thon_18team.chat.poll.dto.PollView;
import com.dotlo.line4thon_18team.city.CityController;
import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.global.llm.StructuredLlmExecutor;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.version.repository.ItineraryVersionRepository;
import com.dotlo.line4thon_18team.place.PlaceMatcher;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 투표 후보 만들기 (COLLAB-POLL-002). 동행자들이 고른 조율 방향에 맞는 장소를 찾아 투표를 만든다.
 *
 * <pre>
 *   조율 방향 + 대화 → [AI가 검색어 정함] → [서버가 장소 검색] → [AI가 고름] → 투표 생성
 * </pre>
 *
 * <p>AI 에게 장소 이름을 묻지 않는다. 모델이 아는 장소가 그 도시에 실제로 있는지, 지금도 영업하는지
 * 알 수 없어서다. 검색은 서버가 하고 모델은 검색어와 고르기만 맡는다.
 *
 * <p>트랜잭션을 걸지 않는다. AI 두 번과 장소 검색을 기다리는 동안 DB 연결을 쥐고 있지 않기 위해서다.
 * 투표 저장은 {@link PollService#create}가 자기 트랜잭션에서 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PollCandidateService {

    private static final Logger log = LoggerFactory.getLogger(PollCandidateService.class);

    /** 장소 검색과 같은 50km. 도시 밖 장소는 일정에 넣을 수 없다. */
    private static final int SEARCH_RADIUS_M = 50_000;

    /** 투표에 올릴 개수. 검색 결과가 적으면 그 수만큼 줄어든다. */
    private static final int WANTED = 3;

    private final TripService tripService;
    private final PlaceMatcher placeMatcher;
    private final ItineraryVersionRepository itineraryVersionRepository;
    private final PollService pollService;
    private final StructuredLlmExecutor llm;
    private final PollSearchTask searchTask;
    private final PollPickTask pickTask;
    private final ObjectMapper objectMapper;

    /**
     * @throws BusinessException 구성원이 아닌 여행·없는 여행(404), 지원하지 않는 도시(400),
     *                           투표에 올릴 장소를 찾지 못함(400)
     */
    public PollView create(long tripId, long userId, PollCandidateCommand command) {
        Trip trip = tripService.findMemberTrip(userId, tripId);
        CityController.City city = CityController.findByName(trip.getCity())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_CITY));

        List<String> lines = command.messages() == null ? List.of()
                : command.messages().stream().map(PollCandidateCommand.Message::text).toList();
        PollSearchOutput plan = llm.execute(searchTask,
                new PollSearchInput(trip.getCity(), command.direction(), lines)).value();

        List<PlaceMatcher.Candidate> found = search(plan.queries(), city, placeIdsInItinerary(tripId));
        if (found.size() < PollService.MIN_CANDIDATES) {
            throw new BusinessException(ErrorCode.NO_POLL_CANDIDATES,
                    ErrorCode.NO_POLL_CANDIDATES.getMessage() + " 찾은 곳: " + found.size() + "곳.");
        }

        int pickCount = Math.min(WANTED, found.size());
        PollPickInput pickInput = new PollPickInput(command.direction(), pickCount, numbered(found));
        PollPickOutput picked = llm.execute(pickTask, pickInput).value();

        List<NewCandidate> candidates = picked.picks().stream()
                .map(pick -> {
                    PlaceMatcher.Candidate place = found.get(pick.number() - 1);
                    return new NewCandidate(place.placeId(), place.name(), pick.tagline());
                })
                .toList();
        return pollService.create(tripId, userId, plan.question(), candidates);
    }

    /** 검색어마다 한 번씩 찾고 place ID 로 겹치는 것을 지운다. 이미 일정에 있는 곳은 뺀다. */
    private List<PlaceMatcher.Candidate> search(List<String> queries, CityController.City city,
                                                Set<String> inItinerary) {
        Map<String, PlaceMatcher.Candidate> byPlaceId = new LinkedHashMap<>();
        for (String query : queries) {
            List<PlaceMatcher.Candidate> hits = placeMatcher.searchNear(query, city.lat(), city.lng(),
                    SEARCH_RADIUS_M);
            log.info("투표 후보 검색 query={} 결과={}곳", query, hits.size());
            hits.stream()
                    .filter(hit -> !inItinerary.contains(hit.placeId()))
                    .forEach(hit -> byPlaceId.putIfAbsent(hit.placeId(), hit));
        }
        return List.copyOf(byPlaceId.values());
    }

    /**
     * 지금 일정에 들어 있는 장소. 이미 가기로 한 곳이 후보로 올라오면 투표할 이유가 없다.
     * 저장된 일정이 없으면 비어 있다.
     */
    private Set<String> placeIdsInItinerary(long tripId) {
        return itineraryVersionRepository.findFirstByTripIdOrderByVersionDesc(tripId)
                .map(version -> {
                    ItineraryDraft draft = objectMapper.readValue(version.getContent(), ItineraryDraft.class);
                    Set<String> placeIds = new LinkedHashSet<>();
                    draft.days().forEach(day -> day.visits().forEach(visit -> placeIds.add(visit.placeId())));
                    return placeIds;
                })
                .orElseGet(Set::of);
    }

    private static List<PollPickInput.Found> numbered(List<PlaceMatcher.Candidate> found) {
        List<PollPickInput.Found> places = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            places.add(new PollPickInput.Found(i + 1, found.get(i).name(), found.get(i).address()));
        }
        return places;
    }
}
