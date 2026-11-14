package com.dotlo.line4thon_18team.itinerary;

import com.dotlo.line4thon_18team.chat.poll.repository.TripPollRepository;
import com.dotlo.line4thon_18team.chat.poll.repository.TripPollVoteRepository;
import com.dotlo.line4thon_18team.itinerary.edit.repository.ItineraryEditRequestRepository;
import com.dotlo.line4thon_18team.itinerary.version.repository.ItineraryVersionRepository;
import com.dotlo.line4thon_18team.preference.PreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여행을 지울 때 BE3가 그 여행에 딸려 만든 데이터를 정리한다. 여행 삭제에서 이 메서드 하나만 부르면 된다.
 *
 * <p>테이블마다 붙어 있는 방식이 다르다.
 * <ul>
 *   <li>{@code itinerary_edit_request} — trip_id 외래키. 안 지우면 여행 삭제가 실패한다</li>
 *   <li>{@code itinerary_version} — trip_id 외래키. 같다</li>
 *   <li>{@code trip_preferences} — trip_id 가 외래키 없는 컬럼이다. 삭제는 성공하지만 지우지 않으면
 *       고아 행이 조용히 남는다. 제약이 막아주지 않아서 더 조심해야 하는 쪽이다</li>
 *   <li>{@code trip_poll} — trip_id 외래키. 표가 투표와 후보를 가리켜서 표를 먼저 지운다.
 *       후보는 투표를 지울 때 함께 사라진다</li>
 * </ul>
 *
 * <p>투표는 채팅방 기능이지만 BE3 가 만든 데이터라 여기서 같이 정리한다. 여행 삭제 쪽에서
 * 부를 메서드를 늘리지 않으려는 것이다.
 *
 * <p>벌크 delete({@code @Modifying @Query})를 쓰지 않는다. 쿼리는 한 번으로 줄지만
 * {@code clearAutomatically} 로 영속성 컨텍스트를 비워야 하고, 그러면 부르는 쪽이 들고 있던
 * Trip 엔티티가 떨어져 나가서 바로 뒤의 여행 삭제가 깨진다. 파생 쿼리는 행 수만큼 delete 가
 * 나가지만 여행 삭제는 드문 작업이다.
 *
 * <p>권한 확인은 하지 않는다. 부르는 쪽(여행 삭제)이 이미 내 여행인지 보고 들어온다.
 */
@Service
@RequiredArgsConstructor
public class ItineraryTripCleanupService {

    private final ItineraryEditRequestRepository editRequestRepository;
    private final ItineraryVersionRepository itineraryVersionRepository;
    private final PreferenceRepository preferenceRepository;
    private final TripPollVoteRepository pollVoteRepository;
    private final TripPollRepository pollRepository;

    @Transactional
    public void deleteByTrip(long tripId) {
        editRequestRepository.deleteByTripId(tripId);
        itineraryVersionRepository.deleteByTripId(tripId);
        preferenceRepository.deleteByTrip(tripId);
        pollVoteRepository.deleteByPoll_Trip_Id(tripId);
        pollRepository.deleteByTripId(tripId);
    }
}
