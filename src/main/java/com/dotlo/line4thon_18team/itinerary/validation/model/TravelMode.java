package com.dotlo.line4thon_18team.itinerary.validation.model;

/** 이동 수단. 렌터카를 빌린 날은 긴 구간을 DRIVE로 본다 (가까운 곳은 그 날도 걷는다). */
public enum TravelMode {
    WALK,
    TRANSIT,
    DRIVE
}
