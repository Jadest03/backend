package com.dotlo.line4thon_18team.chat.poll;

import com.dotlo.line4thon_18team.chat.poll.dto.NewCandidate;
import com.dotlo.line4thon_18team.chat.poll.dto.PollView;
import com.dotlo.line4thon_18team.chat.poll.entity.TripPoll;
import com.dotlo.line4thon_18team.chat.poll.entity.TripPollCandidate;
import com.dotlo.line4thon_18team.chat.poll.entity.TripPollVote;
import com.dotlo.line4thon_18team.chat.poll.repository.TripPollRepository;
import com.dotlo.line4thon_18team.chat.poll.repository.TripPollVoteRepository;
import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 채팅방 투표 (COLLAB-POLL-001). 후보를 만드는 것은 @도틀로 투표 만들기(AI)뿐이라 생성 API 는 없고,
 * AI 쪽에서 {@link #create}를 부른다.
 *
 * <p>구성원이 모두 투표하면 그 자리에서 마감한다. 따로 마감하는 버튼이 화면에 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PollService {

    /** 후보가 2개 미만이면 고를 게 없고, 너무 많으면 화면에서 고르기 어렵다. */
    public static final int MIN_CANDIDATES = 2;
    static final int MAX_CANDIDATES = 5;

    /** 채팅 조회에 함께 내려줄 최근 투표 수. */
    static final int RECENT_LIMIT = 20;

    private final TripService tripService;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripPollRepository pollRepository;
    private final TripPollVoteRepository voteRepository;

    /**
     * 투표를 만든다. 투표 후보 만들기(AI)가 부른다.
     *
     * @throws BusinessException 구성원이 아닌 여행·없는 여행(404), 후보 개수가 범위 밖(400)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PollView create(long tripId, long userId, String question, List<NewCandidate> candidates) {
        Trip trip = lockMemberTrip(tripId, userId);
        if (candidates == null || candidates.size() < MIN_CANDIDATES || candidates.size() > MAX_CANDIDATES) {
            throw new BusinessException(ErrorCode.INVALID_POLL,
                    ErrorCode.INVALID_POLL.getMessage() + " 후보는 " + MIN_CANDIDATES + "~" + MAX_CANDIDATES
                            + "곳이어야 합니다 (받은 개수: " + (candidates == null ? 0 : candidates.size()) + ").");
        }
        TripPoll poll = TripPoll.create(trip, question, userId);
        candidates.forEach(c -> poll.addCandidate(c.placeId(), c.name(), c.tagline()));
        return view(pollRepository.save(poll), List.of(), memberCount(tripId));
    }

    /**
     * 후보 하나에 투표한다. 이미 투표했으면 그 표를 옮긴다.
     *
     * @throws BusinessException 구성원이 아닌 여행·없는 여행(404), 없는 투표(404),
     *                           이미 끝난 투표(409), 그 투표의 후보가 아님(400)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PollView vote(long tripId, long userId, long pollId, long candidateId) {
        Trip trip = lockMemberTrip(tripId, userId);
        TripPoll poll = pollRepository.findByIdAndTripId(pollId, tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POLL_NOT_FOUND));
        if (poll.isClosed()) {
            throw new BusinessException(ErrorCode.POLL_CLOSED);
        }
        TripPollCandidate candidate = poll.getCandidates().stream()
                .filter(c -> c.getId().equals(candidateId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_POLL_CANDIDATE));

        voteRepository.findByPollIdAndUserId(pollId, userId)
                .ifPresentOrElse(vote -> vote.changeTo(candidate),
                        () -> voteRepository.save(TripPollVote.create(poll, candidate, userId)));

        Set<Long> memberIds = currentMemberIds(trip);
        List<TripPollVote> votes = voteRepository.findByPollIdOrderByIdAsc(pollId).stream()
                .filter(vote -> memberIds.contains(vote.getUserId()))
                .toList();
        if (votes.size() == memberIds.size()) {
            poll.close();
        }
        return view(poll, votes, memberIds.size());
    }

    /** 채팅 메시지 조회(BE2)가 함께 내려줄 최근 투표 상태. */
    public List<PollView> recentPolls(long tripId, long userId) {
        Trip trip = tripService.findMemberTrip(userId, tripId);
        List<TripPoll> polls = pollRepository.findByTripIdOrderByIdDesc(tripId, Limit.of(RECENT_LIMIT));
        if (polls.isEmpty()) {
            return List.of();
        }
        Set<Long> memberIds = currentMemberIds(trip);
        Map<Long, List<TripPollVote>> votesByPoll = new LinkedHashMap<>();
        voteRepository.findByPollIdInOrderByIdAsc(polls.stream().map(TripPoll::getId).toList()).stream()
                .filter(vote -> memberIds.contains(vote.getUserId()))
                .forEach(vote -> votesByPoll.computeIfAbsent(vote.getPoll().getId(), id -> new ArrayList<>())
                        .add(vote));
        return polls.stream()
                .map(poll -> view(poll, votesByPoll.getOrDefault(poll.getId(), List.of()), memberIds.size()))
                .toList();
    }

    // 여행 삭제·나가기와 같은 잠금 순서로 시작하고, 잠금을 얻은 뒤 구성원을 확인한다.
    private Trip lockMemberTrip(long tripId, long userId) {
        return tripRepository.findByIdForUpdate(tripId)
                .filter(trip -> tripService.isMember(trip, userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
    }

    private Set<Long> currentMemberIds(Trip trip) {
        Set<Long> memberIds = new HashSet<>(tripMemberRepository.findUserIdsByTripId(trip.getId()));
        memberIds.add(trip.getUser().getId());
        return memberIds;
    }

    /** 방장은 trip_member 에 없어서 하나 더한다. */
    private long memberCount(long tripId) {
        return tripMemberRepository.countByTripId(tripId) + 1;
    }

    private static PollView view(TripPoll poll, List<TripPollVote> votes, long members) {
        Map<Long, List<Long>> votersByCandidate = new LinkedHashMap<>();
        votes.forEach(vote -> votersByCandidate
                .computeIfAbsent(vote.getCandidate().getId(), id -> new ArrayList<>())
                .add(vote.getUserId()));
        List<PollView.Candidate> candidates = poll.getCandidates().stream()
                .map(candidate -> {
                    List<Long> voters = votersByCandidate.getOrDefault(candidate.getId(), List.of());
                    return new PollView.Candidate(candidate.getId(), candidate.getPlaceId(), candidate.getName(),
                            candidate.getTagline(), voters.size(), List.copyOf(voters));
                })
                .toList();
        return new PollView(poll.getId(), poll.getQuestion(), poll.getStatus(), members, votes.size(), candidates);
    }
}
