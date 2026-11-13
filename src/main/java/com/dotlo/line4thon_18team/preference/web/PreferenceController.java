package com.dotlo.line4thon_18team.preference.web;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.preference.InterpretationResult;
import com.dotlo.line4thon_18team.preference.PreferenceInput;
import com.dotlo.line4thon_18team.preference.PreferenceService;
import com.dotlo.line4thon_18team.preference.PreferenceView;
import com.dotlo.line4thon_18team.trip.service.TripService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 취향 API (API 명세서 '취향' 파트, BE3). 모두 로그인이 필요하다 ({@link LoginUser}).
 *
 * <p>로컬에서는 AUTH_DEV_HEADER_ENABLED=true 일 때 토큰 대신 X-User-Id 헤더로도 호출할 수 있다.
 *
 * <p>취향은 여행에 붙는 정보라서 모든 API가 먼저 그 여행 구성원인지 확인한다 (아니면 404).
 * 초대를 받아 들어온 동행자도 자기 취향을 넣고 본다. 취향은 (여행, 사용자)마다 한 줄이라
 * 자기 줄만 건드리고 남의 취향은 조회되지 않는다 (동행자 기능에서 합칠 때만 전체를 읽는다).
 * 동행자 기능이 들어오면 소유자가 아니라 구성원인지 확인하는 쪽으로 바꾼다.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/preferences")
public class PreferenceController {

    private final PreferenceService preferenceService;
    private final TripService tripService;

    public PreferenceController(PreferenceService preferenceService, TripService tripService) {
        this.preferenceService = preferenceService;
        this.tripService = tripService;
    }

    /** 텍스트 취향 AI 해석. 해석 결과를 칩으로 반환하고 저장하지 않는다. */
    @PostMapping("/analyze")
    public InterpretationResult analyze(@PathVariable("tripId") long tripId,
                                        @LoginUser Long userId,
                                        @RequestBody InterpretRequest request) {
        tripService.findMemberTrip(userId, tripId);
        return preferenceService.interpret(tripId, userId, request.text());
    }

    /** 취향 칩 저장 (전체 덮어쓰기). */
    @PutMapping
    public PreferenceView save(@PathVariable("tripId") long tripId,
                               @LoginUser Long userId,
                               @RequestBody PreferenceInput input) {
        tripService.findMemberTrip(userId, tripId);
        try {
            return preferenceService.save(tripId, userId, input);
        } catch (DataIntegrityViolationException e) {
            // 처음 저장을 동시에 두 번 보내면 둘 다 없다고 보고 넣다가 (trip_id, user_id) 유니크에 걸린다.
            // 실패한 트랜잭션이 끝난 뒤 다시 저장하면 먼저 생긴 줄을 고치게 된다
            return preferenceService.save(tripId, userId, input);
        }
    }

    /** 취향 조회. 저장 전이면 기본값과 saved=false. */
    @GetMapping
    public PreferenceView get(@PathVariable("tripId") long tripId,
                              @LoginUser Long userId) {
        tripService.findMemberTrip(userId, tripId);
        return preferenceService.get(tripId, userId);
    }
}
