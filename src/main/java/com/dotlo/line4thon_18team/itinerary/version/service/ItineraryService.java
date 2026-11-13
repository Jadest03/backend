package com.dotlo.line4thon_18team.itinerary.version.service;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.itinerary.ItineraryValidationService;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ValidationOrigin;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItinerarySaveRequest;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItineraryVersionSummary;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItineraryVersionView;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItineraryVersion;
import com.dotlo.line4thon_18team.itinerary.version.repository.ItineraryVersionRepository;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItineraryService {

    private final TripService tripService;
    private final ItineraryVersionRepository itineraryVersionRepository;
    private final ItineraryValidationService itineraryValidationService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 일정을 새 버전으로 저장한다 (기존 버전은 지우지 않는다). draft 의 itemId 는 받은 그대로 저장한다.
     *
     * @param baseVersion 고치기 전 버전. 그사이 다른 저장이 있었으면(최신이 아니면) 409. AI 생성처럼 없으면 null
     * @throws BusinessException 없는 여행/구성원이 아님(404), 버전 충돌(409)
     */
    @Transactional
    public ItineraryVersionView save(long tripId, long userId, ItinerarySource source, Integer baseVersion,
                                     ItineraryDraft draft, ValidationResult validation) {
        return saveVersion(tripId, userId, source, baseVersion, baseVersion, draft, validation);
    }

    // expectedLatest: 저장하는 순간 최신 버전이 이 값이어야 한다 (아니면 409). null 이면 확인하지 않는다
    private ItineraryVersionView saveVersion(long tripId, long userId, ItinerarySource source, Integer baseVersion,
                                             Integer expectedLatest, ItineraryDraft draft, ValidationResult validation) {
        Trip trip = tripService.findMemberTrip(userId, tripId);
        int latest = itineraryVersionRepository.findFirstByTripIdOrderByVersionDesc(tripId)
                .map(ItineraryVersion::getVersion)
                .orElse(0);
        if (expectedLatest != null && expectedLatest != latest) {
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

    /**
     * 사용자가 직접 고친 일정 저장 API (FE). 서버가 다시 검증해서 시각을 계산한 뒤 MANUAL_EDIT 으로 저장한다.
     * 검증이 영업시간/이동시간을 외부에서 조회해서, 기다리는 동안 DB 연결을 잡지 않도록 저장할 때만 트랜잭션을 연다.
     *
     * @throws BusinessException 없는 여행/구성원이 아님(404), 잘못된 일정(400), 버전 충돌(409)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ItineraryVersionView saveManualEdit(Long tripId, Long userId, ItinerarySaveRequest request) {
        tripService.findMemberTrip(userId, tripId);
        // 이미 일정이 있는데 고치기 전 버전을 모르면, 다른 사람이 고친 걸 덮어쓸 수 있어서 막는다
        if (request.baseVersion() == null && itineraryVersionRepository.existsByTripId(tripId)) {
            throw new BusinessException(ErrorCode.ITINERARY_VERSION_CONFLICT);
        }

        ItineraryDraft draft = request.toDraft();
        ValidationResult validation = itineraryValidationService.validate(tripId, userId, draft, ValidationOrigin.USER_EDITED);
        // 검증하는 수십 초 사이에 다른 저장이 들어올 수 있어서, 저장하는 순간에 다시 확인한다
        // (baseVersion 이 없으면 "아직 일정이 없음 = 최신 0" 이어야 한다. 저장되는 baseVersion 은 그대로 null)
        Integer expectedLatest = request.baseVersion() == null ? 0 : request.baseVersion();
        return transactionTemplate.execute(status -> saveVersion(tripId, userId, ItinerarySource.MANUAL_EDIT,
                request.baseVersion(), expectedLatest, draft, validation));
    }

    /** 최신 버전. 소유권 확인은 호출하는 쪽에서 한다. */
    public Optional<ItineraryVersionView> findLatest(long tripId) {
        return itineraryVersionRepository.findFirstByTripIdOrderByVersionDesc(tripId).map(this::toView);
    }

    // 최신 일정 조회 API (FE)
    public ItineraryVersionView getLatest(Long tripId, Long userId) {
        tripService.findMemberTrip(userId, tripId);   // 없는 여행/구성원이 아니면 404
        return findLatest(tripId).orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_NOT_FOUND));
    }

    /**
     * 그 버전의 일정으로 되돌린다 (EDIT-REVERT-001).
     *
     * <p>버전을 지우지 않고 새 버전(REVERT)으로 쌓는다. 되돌리기를 되돌릴 수도 있다.
     *
     * <p>그 버전에 저장돼 있던 검증 결과를 그대로 복원한다. 다시 계산하면 "되돌렸는데 시각이 달라지는"
     * 일이 생기고, 영업시간·이동시간 외부 조회로 수십 초가 걸린다. 최신 기준으로 다시 보려면
     * 일정 검증 API({@code POST /itineraries/check})를 쓰면 된다.
     *
     * <p>baseVersion 에는 되돌린 대상이 아니라 되돌리기 직전 최신 버전을 넣는다.
     * {@link #save}가 baseVersion 을 "고치기 전 최신"으로 보고 버전 충돌을 잡기 때문이다.
     * 어느 버전으로 되돌렸는지는 복원된 내용으로 알 수 있다.
     *
     * @throws BusinessException 없는 여행/구성원이 아님(404), 없는 버전(404), 이미 최신(400)
     */
    @Transactional
    public ItineraryVersionView revert(Long tripId, Long userId, int version) {
        tripService.findMemberTrip(userId, tripId);   // 없는 여행/구성원이 아니면 404
        ItineraryVersion target = itineraryVersionRepository.findByTripIdAndVersion(tripId, version)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_VERSION_NOT_FOUND));
        int latest = itineraryVersionRepository.findFirstByTripIdOrderByVersionDesc(tripId)
                .map(ItineraryVersion::getVersion)
                .orElse(0);
        if (version == latest) {
            throw new BusinessException(ErrorCode.ITINERARY_ALREADY_LATEST);
        }
        return save(tripId, userId, ItinerarySource.REVERT, latest,
                objectMapper.readValue(target.getContent(), ItineraryDraft.class),
                objectMapper.readValue(target.getValidation(), ValidationResult.class));
    }

    /**
     * 일정 변경 이력 API (FE). 최신 버전부터 내려준다. 일정 내용과 검증 결과는 담지 않는다.
     *
     * @throws BusinessException 없는 여행/구성원이 아님(404)
     */
    public List<ItineraryVersionSummary> findHistory(Long tripId, Long userId) {
        tripService.findMemberTrip(userId, tripId);   // 없는 여행/구성원이 아니면 404
        return itineraryVersionRepository.findByTripIdOrderByVersionDesc(tripId).stream()
                .map(ItineraryService::toSummary)
                .toList();
    }

    private static ItineraryVersionSummary toSummary(ItineraryVersion itinerary) {
        return new ItineraryVersionSummary(
                itinerary.getVersion(),
                itinerary.getSource(),
                itinerary.getBaseVersion(),
                itinerary.getCreatedBy(),
                itinerary.getCreatedAt()
        );
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
