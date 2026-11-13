package com.dotlo.line4thon_18team.trip;

import com.dotlo.line4thon_18team.itinerary.validation.model.DayDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.validation.model.VisitDraft;
import com.dotlo.line4thon_18team.itinerary.validation.result.ValidationResult;
import com.dotlo.line4thon_18team.itinerary.version.entity.ItinerarySource;
import com.dotlo.line4thon_18team.itinerary.version.repository.ItineraryVersionRepository;
import com.dotlo.line4thon_18team.itinerary.version.service.ItineraryService;
import com.dotlo.line4thon_18team.member.entity.TripInvite;
import com.dotlo.line4thon_18team.member.entity.TripMember;
import com.dotlo.line4thon_18team.member.repository.TripInviteRepository;
import com.dotlo.line4thon_18team.member.repository.TripMemberRepository;
import com.dotlo.line4thon_18team.place.entity.SavedPlace;
import com.dotlo.line4thon_18team.place.repository.SavedPlaceRepository;
import com.dotlo.line4thon_18team.share.entity.TripShare;
import com.dotlo.line4thon_18team.share.repository.TripShareRepository;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 여행 삭제(방장만)와 여행 나가기. MySQL이 필요하다 (docker compose up -d).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "auth.dev-header-enabled=true")
@Transactional
class TripDeleteLeaveTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 10);

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TripRepository trips;
    @Autowired TripMemberRepository tripMembers;
    @Autowired TripInviteRepository tripInvites;
    @Autowired TripShareRepository tripShares;
    @Autowired SavedPlaceRepository savedPlaces;
    @Autowired ItineraryVersionRepository itineraryVersions;
    @Autowired ItineraryService itineraryService;

    private User owner;
    private User first;
    private User second;
    private Trip trip;

    @BeforeEach
    void setUp() {
        owner = users.save(newUser());
        first = users.save(newUser());
        second = users.save(newUser());
        trip = trips.save(Trip.create(owner, "오사카", START, START.plusDays(2), Companion.FRIEND, null));
    }

    @Test
    void 방장이_여행을_지우면_딸린_데이터도_같이_지워진다() throws Exception {
        tripMembers.save(TripMember.create(trip, first));
        tripInvites.save(TripInvite.create(trip, "delete-" + UUID.randomUUID().toString().substring(0, 8),
                owner.getId(), LocalDateTime.now().plusDays(1)));
        tripShares.save(TripShare.create(trip, "share-" + UUID.randomUUID().toString().substring(0, 8)));
        savedPlaces.save(SavedPlace.fromTakeout(trip, null, "도톤보리", null, null, null, null, "오사카"));
        itineraryService.save(trip.getId(), owner.getId(), ItinerarySource.AI_GENERATED, null,
                ItineraryDraft.of(DayDraft.of(START, VisitDraft.of("v-3f9a1c20", "ChIJ-a"))),
                ValidationResult.of(List.of(), List.of()));

        perform(delete("/api/trips/{tripId}", trip.getId()), owner).andExpect(status().isNoContent());

        assertThat(trips.findById(trip.getId())).isEmpty();
        assertThat(tripMembers.countByTripId(trip.getId())).isZero();
        assertThat(savedPlaces.countByTripId(trip.getId())).isZero();
        assertThat(tripShares.findByTripId(trip.getId())).isEmpty();
        assertThat(itineraryVersions.existsByTripId(trip.getId())).isFalse();
    }

    @Test
    void 멤버는_여행을_지울_수_없고_구성원이_아니면_404() throws Exception {
        tripMembers.save(TripMember.create(trip, first));

        perform(delete("/api/trips/{tripId}", trip.getId()), first)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TRIP_OWNER_ONLY"));
        perform(delete("/api/trips/{tripId}", trip.getId()), second)
                .andExpect(status().isNotFound());
        assertThat(trips.findById(trip.getId())).isPresent();
    }

    @Test
    void 멤버가_나가면_구성원에서_빠지고_여행은_남는다() throws Exception {
        tripMembers.save(TripMember.create(trip, first));

        leave(first).andExpect(status().isOk()).andExpect(jsonPath("$.tripDeleted").value(false));

        perform(get("/api/trips/{tripId}", trip.getId()), first).andExpect(status().isNotFound());
        perform(get("/api/trips/{tripId}/members", trip.getId()), owner)
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 방장이_나가면_가장_먼저_참여한_멤버가_방장이_된다() throws Exception {
        tripMembers.save(TripMember.create(trip, first));
        tripMembers.save(TripMember.create(trip, second));

        leave(owner).andExpect(status().isOk()).andExpect(jsonPath("$.tripDeleted").value(false));

        perform(get("/api/trips/{tripId}", trip.getId()), owner).andExpect(status().isNotFound());
        perform(get("/api/trips/{tripId}/members", trip.getId()), first)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(first.getId()))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[1].userId").value(second.getId()));
    }

    @Test
    void 혼자인_방장이_나가면_여행이_지워진다() throws Exception {
        leave(owner).andExpect(status().isOk()).andExpect(jsonPath("$.tripDeleted").value(true));

        assertThat(trips.findById(trip.getId())).isEmpty();
    }

    @Test
    void 구성원이_아니면_나갈_수_없다() throws Exception {
        leave(second)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }

    private ResultActions leave(User user) throws Exception {
        return perform(delete("/api/trips/{tripId}/members/me", trip.getId()), user);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, User user) throws Exception {
        return mvc.perform(request.header("X-User-Id", user.getId()));
    }

    private static User newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return User.builder().googleSub("sub-leave-" + id).email(id + "@test.com").name("테스트" + id).build();
    }
}
