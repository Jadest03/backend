package com.dotlo.line4thon_18team.itinerary.version.service;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItineraryVersionView;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItineraryVersion;
import com.dotlo.line4thon_18team.itinerary.version.repository.ItineraryVersionRepository;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItineraryService {

    private final TripService tripService;
    private final ItineraryVersionRepository itineraryVersionRepository;
    private final ObjectMapper objectMapper;

    /**
     * 일정을 새 버전으로 저장한다 (기존 버전은 지우지 않는다). draft 의 itemId 는 받은 그대로 저장한다.
     *
     * @param baseVersion 고치기 전 버전. 그사이 다른 저장이 있었으면(최신이 아니면) 409. AI 생성처럼 없으면 null
     * @throws BusinessException 없는 여행/남의 여행(404), 버전 충돌(409)
     */
    @Transactional
    public ItineraryVersionView save(long tripId, long userId, ItinerarySource source, Integer baseVersion,
                                     ItineraryDraft draft, ValidationResult validation) {
        Trip trip = tripService.findOwnedTrip(userId, tripId);
        int latest = itineraryVersionRepository.findFirstByTripIdOrderByVersionDesc(tripId)
                .map(ItineraryVersion::getVersion)
                .orElse(0);
        if (baseVersion != null && baseVersion != latest) {
            throw new BusinessException(ErrorCode.ITINERARY_VERSION_CONFLICT);
        }

        ItineraryVersion itinerary = ItineraryVersion.create(trip, latest + 1, baseVersion, source, userId,
                objectMapper.writeValueAsString(draft), objectMapper.writeValueAsString(validation));
        try {
            return toView(itineraryVersionRepository.saveAndFlush(itinerary));
        } catch (DataIntegrityViolationException e) {
            // 같은 여행에 저장이 동시에 들어와 같은 버전 번호를 받은 경우
            throw new BusinessException(ErrorCode.ITINERARY_VERSION_CONFLICT);
        }
    }

    public Optional<ItineraryVersionView> findLatest(long tripId) {
        return itineraryVersionRepository.findFirstByTripIdOrderByVersionDesc(tripId).map(this::toView);
    }

    // 최신 일정 조회 API (FE)
    public ItineraryVersionView getLatest(Long userId, Long tripId) {
        tripService.findOwnedTrip(userId, tripId);   // 없는 여행/남의 여행이면 404
        return findLatest(tripId).orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_NOT_FOUND));
    }

    private ItineraryVersionView toView(ItineraryVersion itinerary) {
        return new ItineraryVersionView(
                itinerary.getVersion(),
                itinerary.getSource(),
                itinerary.getBaseVersion(),
                itinerary.getCreatedBy(),
                itinerary.getCreatedAt(),
                objectMapper.readValue(itinerary.getContent(), ItineraryDraft.class),
                itinerary.getValidation()
        );
    }
}
