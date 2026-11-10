# 인기 코스 조회 API

공용 코스 원본의 목록·상세를 조회한다. 두 API 모두 로그인 없이 사용할 수 있다.
기존 JWT 필터는 적용되므로 잘못되거나 만료된 Bearer 토큰을 보내면 401이다.
새 환경변수는 없다.

## 목록 조회

### Request

```http
GET /api/courses
GET /api/courses?city=교토
```

| 입력 | 규칙 |
| --- | --- |
| city | 생략하면 전체 도시. 전달하면 앞뒤 공백을 제거한 1~100자 도시 이름으로 완전 일치 검색 |

등록 시각 내림차순, 같은 시각이면 id 내림차순으로 반환한다.
등록된 코스가 없으면 200과 빈 배열 `[]`이다. 등록하지 않은 도시도 빈 배열이다.
목록에는 일차별 장소를 포함하지 않는다.

### Response 200

아래 코스·이미지·장소 ID는 형식을 보여주는 예시이며 실제 서비스 데이터가 아니다.

```json
[
  {
    "id": 1,
    "title": "교토 골목 산책",
    "country": "일본",
    "city": "교토",
    "dayCount": 3,
    "nightCount": 2,
    "stopCount": 1,
    "coverImageUrl": "https://example.test/kyoto.jpg",
    "transportSummary": "도보와 버스",
    "author": {
      "name": "등록자",
      "imageUrl": null,
      "sourceLabel": "직접 다녀온 여행",
      "travelMonth": "2026-03"
    },
    "tags": ["산책", "전통"]
  }
]
```

| 응답 | 의미 |
| --- | --- |
| dayCount / nightCount | 전체 일수 / 숙박 수. 당일 코스는 1 / 0 |
| stopCount | 방문 항목 수. 같은 장소를 다른 일차에 방문하면 각각 셈 |
| author | 표시용 작성자 정보. 미등록 항목은 null. travelMonth는 실제 여행 연월이며 최신순 정렬 기준이 아님 |
| tags | 등록 순서의 코스 태그. 없으면 빈 배열 |
| coverImageUrl / transportSummary | 등록된 대표 이미지 / 원본 이동 방법 소개. 미등록이면 null |

### Error

| 상황 | 상태 | code |
| --- | --- | --- |
| city가 공백이거나 공백 제거 후 100자 초과 | 400 | INVALID_INPUT |

## 상세 조회

### Request

```http
GET /api/courses/{courseId}
```

courseId는 양수인 숫자 id다. 사용자 여행이나 저장 장소 id와 별개다.

### Response 200

```json
{
  "id": 1,
  "title": "교토 골목 산책",
  "country": "일본",
  "city": "교토",
  "dayCount": 3,
  "nightCount": 2,
  "stopCount": 1,
  "coverImageUrl": "https://example.test/kyoto.jpg",
  "description": "아침부터 천천히 걷는 코스",
  "transportSummary": "도보와 버스",
  "author": {
    "name": "등록자",
    "imageUrl": null,
    "sourceLabel": "직접 다녀온 여행",
    "travelMonth": "2026-03"
  },
  "tags": ["산책", "전통"],
  "days": [
    {
      "dayNumber": 1,
      "stopCount": 1,
      "stops": [
        {
          "id": 11,
          "position": 1,
          "placeId": "example-place-id",
          "name": "첫 장소",
          "category": "CULTURE",
          "latitude": 34.99,
          "longitude": 135.78,
          "visitTime": "09:00:00",
          "imageUrl": null
        }
      ]
    },
    {"dayNumber": 2, "stopCount": 0, "stops": []},
    {"dayNumber": 3, "stopCount": 0, "stops": []}
  ]
}
```

- days는 1일차부터 dayCount까지 일차 순서다. 일차 행이나 장소가 없는 날도 빈 stops로 반환한다.
- stops는 방문 순서(position)대로 반환한다.
- stops의 id는 원본 코스 방문 항목 id, placeId는 구글 장소 id다.
- visitTime은 등록한 원본의 예시 현지 시각이다. 없으면 null. 사용자 여행에 적용한 확정 시각은 아니다.
- category는 기존 장소 카테고리 9개를 사용한다. 코스 tags와는 별개다.
- 좌표와 방문 순서로 지도에 번호 핀을 표시할 수 있다.
- 구간별 실제 이동시간·거리·영업시간은 이 API가 조회하거나 저장하지 않는다. 사용자 여행에 담기와 일정 검증 연결은 후속 작업이다.

### Error

| 상황 | 상태 | code |
| --- | --- | --- |
| id가 숫자가 아님, Long 범위 초과, 0 또는 음수 | 400 | INVALID_INPUT |
| 양수 id지만 등록된 코스가 없음 | 404 | COURSE_NOT_FOUND |

## 로컬 호출 예시

```bash
curl --get 'http://localhost:18080/api/courses' --data-urlencode 'city=교토'
curl 'http://localhost:18080/api/courses/1'
```

실제 courseId는 목록 응답에서 확인한다. 코스 등록은 저장 구조를 통해 수행하며 등록용 HTTP API는 이번 범위에 포함하지 않는다.
