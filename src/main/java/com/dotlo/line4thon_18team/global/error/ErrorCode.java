package com.dotlo.line4thon_18team.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 서버에서 내려주는 모든 에러의 목록.
 * 새 에러가 필요하면 자기 도메인 구역에 한 줄 추가하고 BusinessException 으로 던지면 된다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===== 공통 =====
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청 본문(JSON) 형식이 올바르지 않습니다."),
    FILE_REQUIRED(HttpStatus.BAD_REQUEST, "업로드할 파일이 없습니다. 'file' 이름으로 파일을 첨부해 주세요."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "파일 크기가 너무 큽니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type 입니다. JSON 으로 보내 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 서비스 응답에 실패했습니다. 잠시 후 다시 시도해 주세요."),

    // ===== 계정 (BE1) =====
    GOOGLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "구글 로그인에 실패했습니다. 다시 시도해 주세요."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다. 다시 로그인해 주세요."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다. 토큰을 재발급해 주세요."),
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "한글, 영문, 숫자 2~12자로 쓸 수 있어요."),
    NICKNAME_TAKEN(HttpStatus.CONFLICT, "이미 다른 사람이 쓰고 있는 닉네임이에요."),
    AGREEMENT_REQUIRED(HttpStatus.BAD_REQUEST, "필수 약관에 모두 동의해 주세요."),
    ALREADY_SIGNED_UP(HttpStatus.CONFLICT, "이미 가입을 마쳤어요."),

    // ===== 여행 (BE1) =====
    TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "종료일이 시작일보다 빠를 수 없습니다."),
    INVALID_COMPANION_COUNT(HttpStatus.BAD_REQUEST, "동행 인원이 올바르지 않습니다. 친구·가족은 2~10명이에요."),
    UNSUPPORTED_CITY(HttpStatus.BAD_REQUEST, "지원하지 않는 도시입니다."),
    TRIP_OWNER_ONLY(HttpStatus.FORBIDDEN, "여행 삭제는 방장만 할 수 있어요."),

    // ===== 장소 (BE1 / BE2) =====
    CITY_NOT_FOUND(HttpStatus.NOT_FOUND, "도시를 찾을 수 없습니다."),
    EMPTY_FILE(HttpStatus.BAD_REQUEST, "업로드한 파일이 비어 있습니다."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "CSV 또는 zip 파일만 업로드할 수 있습니다."),
    INVALID_CSV_FORMAT(HttpStatus.BAD_REQUEST, "Google Takeout 저장 목록 CSV 형식이 아닙니다."),
    INVALID_ZIP_FILE(HttpStatus.BAD_REQUEST, "zip 파일을 열 수 없습니다."),
    NO_CSV_IN_ZIP(HttpStatus.BAD_REQUEST, "zip 안에 Takeout 저장 목록 CSV가 없습니다."),
    NO_SAVED_PLACES(HttpStatus.BAD_REQUEST, "파일에서 저장된 장소를 찾지 못했습니다."),
    SAVED_PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "저장 장소를 찾을 수 없습니다."),
    INVALID_PLACE_ID(HttpStatus.BAD_REQUEST, "존재하지 않는 장소입니다."),
    PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "장소 사진을 찾을 수 없습니다."),
    PLACE_NOT_REVIEWABLE(HttpStatus.CONFLICT, "확인 필요 상태인 장소만 후보를 선택할 수 있습니다."),
    INVALID_LIST_URL(HttpStatus.BAD_REQUEST, "구글맵 공유 리스트 링크가 아닙니다."),
    LIST_NOT_ACCESSIBLE(HttpStatus.BAD_REQUEST, "리스트를 열 수 없습니다. 공개된 공유 리스트 링크인지 확인해 주세요."),
    LIST_PRIVATE(HttpStatus.BAD_REQUEST, "비공개 리스트입니다. 리스트를 공개로 바꾼 뒤 다시 시도해 주세요."),

    // ===== 예약 정보: 항공·숙소·렌터카 (BE2) =====
    INVALID_STAY_DATES(HttpStatus.BAD_REQUEST, "숙소 날짜가 올바르지 않습니다. 여행 기간 안에서 숙소끼리 겹치지 않게 골라 주세요."),
    INVALID_RENTAL(HttpStatus.BAD_REQUEST, "렌터카 정보가 올바르지 않습니다. 여행 기간 안의 날짜와 픽업 장소를 골라 주세요."),

    // ===== 인기 코스 (BE2) =====
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다."),

    // ===== 알림 (BE2) =====
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),

    // ===== 일정·AI (BE3) =====
    PREFERENCE_TEXT_TOO_LONG(HttpStatus.BAD_REQUEST, "취향은 500자 이하로 입력해 주세요."),
    INVALID_PREFERENCE(HttpStatus.BAD_REQUEST, "취향 값이 올바르지 않습니다."),
    INVALID_ITINERARY(HttpStatus.BAD_REQUEST, "일정 정보가 올바르지 않습니다."),
    NO_PLACES_TO_PLAN(HttpStatus.BAD_REQUEST, "일정을 만들 장소가 없습니다. 장소를 먼저 추가해 주세요."),
    STAY_REQUIRED(HttpStatus.BAD_REQUEST, "숙소를 입력하지 않은 밤이 있습니다. 비어 있는 밤의 숙소를 입력해 주세요."),
    EDIT_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "적용할 수정안이 없습니다. 수정 요청을 다시 보내 주세요."),
    PLACE_NOT_PLANNABLE(HttpStatus.BAD_REQUEST, "이 장소는 일정에 넣을 수 없습니다."),
    PLACE_ALREADY_IN_ITINERARY(HttpStatus.BAD_REQUEST, "이미 일정에 있는 장소입니다."),
    PLACE_ADD_FAILED(HttpStatus.BAD_REQUEST, "장소를 넣을 자리를 찾지 못했습니다."),

    // ===== 일정 저장 (BE1) =====
    ITINERARY_NOT_FOUND(HttpStatus.NOT_FOUND, "저장된 일정이 없습니다."),
    ITINERARY_VERSION_CONFLICT(HttpStatus.CONFLICT, "그사이 일정이 바뀌었습니다. 최신 일정을 다시 불러와 주세요."),
    ITINERARY_VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "그 버전의 일정을 찾을 수 없습니다."),
    ITINERARY_ALREADY_LATEST(HttpStatus.BAD_REQUEST, "이미 최신 일정입니다. 되돌릴 필요가 없습니다."),

    // ===== 확정/공유 (BE1) =====
    ITINERARY_REQUIRED(HttpStatus.BAD_REQUEST, "확정할 일정이 없습니다. 일정을 먼저 만들어 주세요."),
    TRIP_NOT_CONFIRMED(HttpStatus.BAD_REQUEST, "일정을 확정한 여행만 공유할 수 있습니다."),
    SHARE_NOT_FOUND(HttpStatus.NOT_FOUND, "공유된 일정을 찾을 수 없습니다."),

    // ===== 동행 (BE1) =====
    INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "초대 링크를 찾을 수 없습니다."),
    INVITE_EXPIRED(HttpStatus.BAD_REQUEST, "초대가 만료됐어요. 초대한 친구에게 새 링크를 받아 주세요."),
    TRIP_MEMBER_FULL(HttpStatus.BAD_REQUEST, "여행 인원이 가득 찼어요. (최대 10명)"),

    ; // 에러 추가는 이 위에 (각 줄은 쉼표로 끝내기)

    private final HttpStatus status;
    private final String message;
}
