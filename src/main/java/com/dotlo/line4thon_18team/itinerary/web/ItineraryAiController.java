package com.dotlo.line4thon_18team.itinerary.web;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.itinerary.GeneratedItineraryVersion;
import com.dotlo.line4thon_18team.itinerary.ItineraryEditRequestService;
import com.dotlo.line4thon_18team.itinerary.ItineraryGenerationService;
import com.dotlo.line4thon_18team.itinerary.ItineraryPlaceAddService;
import com.dotlo.line4thon_18team.itinerary.edit.dto.ItineraryEditCommand;
import com.dotlo.line4thon_18team.itinerary.edit.dto.ItineraryEditPreview;
import com.dotlo.line4thon_18team.itinerary.edit.dto.ItineraryPlaceAddCommand;
import com.dotlo.line4thon_18team.itinerary.edit.dto.ItineraryPlaceAddResult;
import com.dotlo.line4thon_18team.itinerary.edit.dto.ItineraryPlaceAddSuggestion;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItineraryVersionView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 일정 API (BE3). 로그인이 필요하고 남의 여행이면 404다.
 *
 * <p>텍스트 수정(edit-requests)과 수정 적용(apply)도 여기에 붙인다. 저장·조회(BE1)는
 * {@code itinerary.version} 쪽 컨트롤러가 맡는다.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/itineraries")
@RequiredArgsConstructor
public class ItineraryAiController {

    private final ItineraryGenerationService generationService;
    private final ItineraryEditRequestService editRequestService;
    private final ItineraryPlaceAddService placeAddService;

    /**
     * 저장한 장소로 일정을 만들어 새 버전으로 저장한다.
     *
     * <p>AI 호출과 이동시간 조회가 들어가서 응답이 수십 초 걸릴 수 있다.
     * 장소가 하나도 매칭되지 않았으면 400, 숙소를 일부 밤만 넣었으면 400이다.
     */
    @PostMapping("/generate")
    public GeneratedItineraryVersion generate(@PathVariable("tripId") long tripId, @LoginUser Long userId) {
        return generationService.generateAndSave(tripId, userId);
    }

    /**
     * 사용자가 적은 요청대로 AI가 저장된 최신 일정을 고쳐 수정안을 돌려준다. 아직 저장하지 않는다.
     *
     * <p>적용은 {@code POST /itineraries/{version}/apply}로 하고, {@code {version}} 자리에 이 응답의
     * baseVersion 을 보낸다. 적용하지 않으면 현재 일정은 그대로여서 따로 취소할 것이 없다.
     *
     * <p>생성과 마찬가지로 AI 호출과 이동시간 조회가 들어가서 응답이 수십 초 걸릴 수 있다.
     * 저장된 일정이 없으면 404다.
     */
    @PostMapping("/edit-requests")
    public ItineraryEditPreview requestEdit(@PathVariable("tripId") long tripId, @LoginUser Long userId,
                                            @Valid @RequestBody ItineraryEditCommand command) {
        return editRequestService.request(tripId, userId, command.message());
    }

    /**
     * 보관한 수정안을 새 버전으로 저장한다. 요청 바디는 없다.
     *
     * <p>{@code {version}}에는 고친 기준 버전(텍스트 수정 요청 응답의 baseVersion)을 보낸다.
     * 되돌리기({@code /{version}/revert})도 같은 자리에 실제 버전 번호를 쓴다.
     *
     * <p>적용할 수정안이 없으면 404, 그사이 일정이 바뀌었으면 409다. 응답은 최신 일정 조회와 같은 모양이다.
     */
    @PostMapping("/{version}/apply")
    public ItineraryVersionView applyEdit(@PathVariable("tripId") long tripId,
                                          @PathVariable("version") int version, @LoginUser Long userId) {
        return editRequestService.apply(tripId, userId, version);
    }

    /**
     * 장소 하나를 AI가 알맞은 날·순서에 넣고 새 버전으로 저장한다. 투표 1위 장소를 일정에 넣을 때 쓴다.
     *
     * <p>저장 장소에 없는 장소면 서버가 구글에서 받아 담은 뒤 넣는다. 미리보기 없이 바로 저장하므로,
     * 마음에 들지 않으면 되돌리기({@code /{version}/revert})로 되돌리면 된다.
     *
     * <p>저장된 일정이 없으면 404, 폐업·도시 밖 장소거나 이미 일정에 있으면 400,
     * AI가 넣을 자리를 못 찾으면 400이다.
     */
    @PostMapping("/add-place")
    public ItineraryPlaceAddResult addPlace(@PathVariable("tripId") long tripId, @LoginUser Long userId,
                                            @Valid @RequestBody ItineraryPlaceAddCommand command) {
        return placeAddService.add(tripId, userId, command.placeId(), command.day(), command.note());
    }

    /**
     * 장소를 어느 날에 넣을지 고르는 화면("언제 넣을까요?")에 쓸 날짜 목록과 추천 날을 돌려준다.
     * 아직 일정을 바꾸지 않는다.
     *
     * <p>동선을 재야 해서 저장 장소에 담는 것까지는 여기서 한다. 그래서 폐업·도시 밖 장소는
     * 이 단계에서 400으로 걸러진다. AI 를 부르지 않아 응답이 빠르다.
     *
     * <p>{@code day} 는 바디의 {@code day} 자리에 그대로 넣어 장소 넣기를 부르면 된다.
     */
    @PostMapping("/add-place/suggest")
    public ItineraryPlaceAddSuggestion suggestDay(@PathVariable("tripId") long tripId, @LoginUser Long userId,
                                                  @Valid @RequestBody ItineraryPlaceAddCommand command) {
        return placeAddService.suggest(tripId, userId, command.placeId());
    }
}
