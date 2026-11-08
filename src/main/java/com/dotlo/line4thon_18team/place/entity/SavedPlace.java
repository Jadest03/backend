package com.dotlo.line4thon_18team.place.entity;

import com.dotlo.line4thon_18team.global.entity.BaseTimeEntity;
import com.dotlo.line4thon_18team.place.PlaceCategory;
import com.dotlo.line4thon_18team.trip.entity.Trip;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * 여행에 저장된 장소 1개.
 * 같은 여행 안에서 같은 장소(feature ID)는 1행만 존재하고, 여러 리스트에 있었다면 listNames 에 쌓인다.
 */
@Entity
@Table(
        name = "saved_place",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_saved_place_trip_feature",
                columnNames = {"trip_id", "feature_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedPlace extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    // 구글맵 URL 안의 "0x...:0x..." 값. 중복 판별 키. 직접 추가·AI 추천 장소는 없을 수 있다.
    @Column(name = "feature_id", length = 100)
    private String featureId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(length = 2000)
    private String googleUrl;

    // enum 은 MySQL ENUM 대신 varchar 로 저장 (값이 추가돼도 컬럼을 고칠 필요가 없게)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private PlaceSource source;

    @ElementCollection
    @CollectionTable(name = "saved_place_list", joinColumns = @JoinColumn(name = "saved_place_id"))
    @Column(name = "list_name", nullable = false)
    private Set<String> listNames = new HashSet<>();

    // ---- BE2 매칭 후 채워지는 값 ----

    @Column(length = 100)
    private String placeId;

    @Column(length = 300)
    private String address;

    private Double latitude;

    private Double longitude;

    // 장소 유형 (일정 검증의 체류시간, 취향의 관심·제외 유형에 사용)
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private PlaceCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private PlaceMatchStatus matchStatus;

    // Takeout 에서 읽은 장소. featureId 가 없는 핀은 URL 의 좌표를 미리 넣어둔다 (매칭 전이라 PENDING)
    public static SavedPlace fromTakeout(Trip trip, String featureId, String name, String note, String googleUrl,
                                         Double latitude, Double longitude, String listName) {
        return imported(PlaceSource.TAKEOUT, trip, featureId, name, note, googleUrl, latitude, longitude, listName);
    }

    // 구글맵 공유 리스트 링크에서 읽은 장소 (매칭 전이라 PENDING)
    public static SavedPlace fromSharedLink(Trip trip, String featureId, String name, String note, String googleUrl,
                                            Double latitude, Double longitude, String listName) {
        return imported(PlaceSource.SHARED_LINK, trip, featureId, name, note, googleUrl, latitude, longitude, listName);
    }

    // BE2 장소 직접 추가: 검색해서 고른 장소. 구글 장소 정보는 applyMatch 로 채운다
    public static SavedPlace fromSearch(Trip trip, String name, String listName) {
        return imported(PlaceSource.MANUAL_SEARCH, trip, null, name, null, null, null, null, listName);
    }

    private static SavedPlace imported(PlaceSource source, Trip trip, String featureId, String name, String note,
                                       String googleUrl, Double latitude, Double longitude, String listName) {
        SavedPlace place = new SavedPlace();
        place.trip = trip;
        place.featureId = featureId;
        place.name = name;
        place.note = note;
        place.googleUrl = googleUrl;
        place.latitude = latitude;
        place.longitude = longitude;
        place.source = source;
        place.matchStatus = PlaceMatchStatus.PENDING;
        place.listNames.add(listName);
        return place;
    }

    // 이미 있던 장소가 다른 리스트에도 있으면 리스트 이름만 추가
    public void addListName(String listName) {
        listNames.add(listName);
    }

    // 메모가 없는 장소(공유 링크로 먼저 들어온 장소)에 나중에 올린 Takeout 메모를 채운다
    public void fillNoteIfEmpty(String note) {
        if (this.note == null && note != null) {
            this.note = note;
        }
    }

    // BE2 보강: 찾은 장소 정보를 채운다 (status: MATCHED / CLOSED / OUT_OF_CITY)
    public void applyMatch(String name, String placeId, String address, double latitude, double longitude,
                           PlaceCategory category, PlaceMatchStatus status) {
        this.name = name;
        this.placeId = placeId;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.category = category;
        this.matchStatus = status;
    }

    // BE2 보강: 장소를 확정하지 못함 (NEEDS_REVIEW / NOT_FOUND). 핀 좌표는 그대로 둔다
    public void updateMatchStatus(PlaceMatchStatus status) {
        this.matchStatus = status;
    }

    // BE2 보강: 같은 장소인 다른 행을 합친다 (리스트 이름, 메모). 다른 행은 호출한 쪽에서 지운다
    public void mergeFrom(SavedPlace other) {
        listNames.addAll(other.listNames);
        if (other.note == null || other.note.isBlank() || other.note.equals(note)) return;
        note = (note == null || note.isBlank()) ? other.note : note + "\n" + other.note;
    }
}
