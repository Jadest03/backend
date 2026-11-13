package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.itinerary.edit.ItineraryDiff;
import com.dotlo.line4thon_18team.itinerary.edit.dto.ItineraryEditPreview;
import com.dotlo.line4thon_18team.itinerary.edit.entity.ItineraryEditRequest;
import com.dotlo.line4thon_18team.itinerary.edit.repository.ItineraryEditRequestRepository;
import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.itinerary.validation.support.OsakaFixtures;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItineraryVersionView;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.service.TripService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 텍스트 수정 요청 조립 (EDIT-TEXT-001): 저장된 최신 일정 읽기 → AI 수정 → 변경점 계산 → 수정안 보관.
 * AI가 어떻게 고치는지는 {@link ItineraryEditServiceTest}에서 본다.
 */
class ItineraryEditRequestServiceTest {

    private static final long TRIP = 1L;
    private static final long USER = 7L;
    private static final String DOTONBORI = "place-dotonbori";
    private static final String KUROMON = "place-kuromon";

    private final ItineraryService itineraryService = Mockito.mock(ItineraryService.class);
    private final ItineraryEditService editService = Mockito.mock(ItineraryEditService.class);
    private final ItineraryEditRequestRepository editRequests = Mockito.mock(ItineraryEditRequestRepository.class);
    private final TripService tripService = Mockito.mock(TripService.class);

    private final ItineraryEditRequestService service = new ItineraryEditRequestService(
            itineraryService, editService, editRequests, tripService,
            directTransaction(), JsonMapper.builder().build());

    @Test
    void 수정안과_변경점과_이유를_함께_돌려준다() {
        givenCurrent(3, twoDays());
        givenEdited(secondDayEmptied(), "구로몬 시장을 뺐습니다.");

        ItineraryEditPreview preview = service.request(TRIP, USER, "둘째 날 하나 빼 주세요");

        assertThat(preview.baseVersion()).isEqualTo(3);
        assertThat(preview.reason()).isEqualTo("구로몬 시장을 뺐습니다.");
        assertThat(preview.itinerary().draft()).isEqualTo(secondDayEmptied());
        assertThat(preview.diff().changesOf(ItineraryDiff.ChangeKind.REMOVED))
                .extracting(ItineraryDiff.ItemChange::itemId)
                .containsExactly("v-bbb");
    }

    @Test
    void 보관한_수정안에_기준_버전과_요청과_이유가_들어간다() {
        givenCurrent(3, twoDays());
        givenEdited(secondDayEmptied(), "구로몬 시장을 뺐습니다.");

        service.request(TRIP, USER, "둘째 날 하나 빼 주세요");

        ArgumentCaptor<ItineraryEditRequest> saved = ArgumentCaptor.forClass(ItineraryEditRequest.class);
        Mockito.verify(editRequests).save(saved.capture());
        assertThat(saved.getValue().getBaseVersion()).isEqualTo(3);
        assertThat(saved.getValue().getMessage()).isEqualTo("둘째 날 하나 빼 주세요");
        assertThat(saved.getValue().getReason()).isEqualTo("구로몬 시장을 뺐습니다.");
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(USER);
        assertThat(saved.getValue().getContent()).contains("v-aaa").doesNotContain("v-bbb");
    }

    @Test
    void 적용하기_전에는_일정으로_저장하지_않는다() {
        givenCurrent(3, twoDays());
        givenEdited(secondDayEmptied(), "구로몬 시장을 뺐습니다.");

        service.request(TRIP, USER, "둘째 날 하나 빼 주세요");

        Mockito.verify(itineraryService, Mockito.never()).save(Mockito.anyLong(), Mockito.anyLong(),
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void 저장된_일정이_없으면_404이고_AI를_부르지_않는다() {
        Mockito.when(itineraryService.getLatest(TRIP, USER))
                .thenThrow(new BusinessException(ErrorCode.ITINERARY_NOT_FOUND));

        assertThatThrownBy(() -> service.request(TRIP, USER, "아무거나"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ITINERARY_NOT_FOUND);

        Mockito.verifyNoInteractions(editService);
        Mockito.verifyNoInteractions(editRequests);
    }

    private static ItineraryDraft twoDays() {
        return ItineraryDraft.of(
                DayDraft.of(OsakaFixtures.DAY1, VisitDraft.of("v-aaa", DOTONBORI)),
                DayDraft.of(OsakaFixtures.DAY2, VisitDraft.of("v-bbb", KUROMON)));
    }

    private static ItineraryDraft secondDayEmptied() {
        return ItineraryDraft.of(
                DayDraft.of(OsakaFixtures.DAY1, VisitDraft.of("v-aaa", DOTONBORI)),
                DayDraft.of(OsakaFixtures.DAY2));
    }

    private void givenCurrent(int version, ItineraryDraft draft) {
        Mockito.when(itineraryService.getLatest(TRIP, USER)).thenReturn(
                new ItineraryVersionView(version, ItinerarySource.AI_GENERATED, null, USER,
                        LocalDateTime.of(2026, 12, 1, 10, 0), draft, "{}"));
        Mockito.when(tripService.findMemberTrip(USER, TRIP)).thenReturn(Mockito.mock(Trip.class));
    }

    private void givenEdited(ItineraryDraft draft, String reason) {
        Mockito.when(editService.edit(Mockito.eq(TRIP), Mockito.eq(USER), Mockito.any(), Mockito.anyString()))
                .thenReturn(new EditedItinerary(draft, ValidationResult.of(List.of(), List.of()), reason, 1));
    }

    /** 수정안 보관만 트랜잭션을 따로 여는 구조라서, 테스트에서는 콜백을 그대로 실행한다. */
    private static TransactionTemplate directTransaction() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }
}
