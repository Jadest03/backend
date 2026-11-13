package com.dotlo.line4thon_18team.share.dto;

import com.dotlo.line4thon_18team.itinerary.validation.model.ItineraryDraft;
import com.dotlo.line4thon_18team.itinerary.version.dto.ItineraryVersionView;
import com.dotlo.line4thon_18team.place.PlaceCategory;
import com.dotlo.line4thon_18team.place.entity.SavedPlace;
import com.dotlo.line4thon_18team.trip.entity.Companion;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.LocalDate;
import java.util.List;

/**
 * 로그인 없이 보는 공유된 일정. 사용자 정보와 장소 메모는 넣지 않는다.
 *
 * @param places 일정에 들어 있는 장소 정보 (일정의 placeId 로 찾아 이름, 주소를 보여주는 용도)
 */
public record SharedItineraryResponse(SharedTrip trip, SharedItinerary itinerary, List<SharedPlace> places) {

    public record SharedTrip(String city, LocalDate startDate, LocalDate endDate, Companion companion, int companionCount) {
    }

    /** @param validation 검증 결과. 화면에 그릴 시각은 여기 days 에 있다 */
    public record SharedItinerary(int version, ItineraryDraft draft, @JsonRawValue String validation) {
    }

    public record SharedPlace(String placeId, String name, String address, Double latitude, Double longitude,
                              PlaceCategory category) {

        public static SharedPlace from(SavedPlace place) {
            return new SharedPlace(place.getPlaceId(), place.getName(), place.getAddress(),
                    place.getLatitude(), place.getLongitude(), place.getCategory());
        }
    }

    public static SharedItineraryResponse of(Trip trip, ItineraryVersionView itinerary, List<SharedPlace> places) {
        return new SharedItineraryResponse(
                new SharedTrip(trip.getCity(), trip.getStartDate(), trip.getEndDate(),
                        trip.getCompanion(), trip.getCompanionCount()),
                new SharedItinerary(itinerary.version(), itinerary.draft(), itinerary.validation()),
                places);
    }
}
