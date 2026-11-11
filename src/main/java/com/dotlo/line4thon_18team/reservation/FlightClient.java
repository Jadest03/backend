package com.dotlo.line4thon_18team.reservation;

import com.dotlo.line4thon_18team.reservation.dto.FlightInfo;
import com.dotlo.line4thon_18team.reservation.dto.FlightInfo.Point;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 항공편 조회 (AeroDataBox, RapidAPI). 편명 + 출발 날짜로 출발·도착 공항과 현지 시각을 찾는다.
 * 무료 플랜: 1번에 2유닛, 월 400유닛. KE723 기준 8달 뒤까지 나오고 10달 뒤는 빈 응답 (2026-11-08 확인)
 */
@Component
public class FlightClient {

    private static final String URL = "https://aerodatabox.p.rapidapi.com/flights/number/{number}/{date}?dateLocalRole=Departure";
    private static final String HOST = "aerodatabox.p.rapidapi.com";
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mmXXX");  // 2026-11-15 08:50+09:00

    private final String apiKey;
    private final RestClient rest;

    public FlightClient(@Value("${aerodatabox.api-key}") String apiKey) {
        this.apiKey = apiKey;
        // 기본값은 타임아웃이 없어서 응답이 없으면 요청이 끝없이 기다림
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    // 못 찾으면 빈 목록. 없는 편명, 아직 시간표가 없는 먼 날짜 모두 204로 옴 (2026-11-08 확인)
    public List<FlightInfo> find(String number, LocalDate date) {
        List<Flight> flights = rest.get().uri(URL, number, date)
                .header("X-RapidAPI-Key", apiKey)
                .header("X-RapidAPI-Host", HOST)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return toInfos(flights);
    }

    // 공항 좌표나 예정 시각이 빠진 항공편은 뺀다 (AeroDataBox 문서: 일부 항공편은 정보가 빠져 있을 수 있음)
    static List<FlightInfo> toInfos(List<Flight> flights) {
        if (flights == null) return List.of();
        return flights.stream()
                .map(f -> {
                    Point dep = point(f.departure());
                    Point arr = point(f.arrival());
                    if (dep == null || arr == null) return null;
                    return new FlightInfo(f.number().replace(" ", ""), airlineName(f.airline()), dep, arr);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // AeroDataBox는 항공사 이름을 영어로 줘서(Korean Air) 테스트 노선(한국 ↔ 일본·태국·베트남) 항공사만 한국어로 바꾼다.
    // 표에 없는 항공사는 영어 이름 그대로
    private static final Map<String, String> KOREAN_AIRLINE_NAMES = Map.ofEntries(
            Map.entry("KE", "대한항공"), Map.entry("OZ", "아시아나항공"), Map.entry("7C", "제주항공"),
            Map.entry("LJ", "진에어"), Map.entry("TW", "티웨이항공"), Map.entry("BX", "에어부산"),
            Map.entry("RS", "에어서울"), Map.entry("ZE", "이스타항공"), Map.entry("RF", "에어로케이"),
            Map.entry("YP", "에어프레미아"),
            Map.entry("JL", "일본항공"), Map.entry("NH", "전일본공수"), Map.entry("MM", "피치항공"),
            Map.entry("GK", "젯스타 재팬"), Map.entry("ZG", "집에어"),
            Map.entry("TG", "타이항공"), Map.entry("FD", "타이 에어아시아"), Map.entry("XJ", "타이 에어아시아 엑스"),
            Map.entry("SL", "타이 라이온에어"), Map.entry("PG", "방콕에어웨이즈"), Map.entry("VZ", "타이 비엣젯"),
            Map.entry("VN", "베트남항공"), Map.entry("VJ", "비엣젯항공"), Map.entry("QH", "뱀부항공"));

    static String airlineName(Airline airline) {
        if (airline == null) return null;
        if (airline.iata() == null) return airline.name();   // Map.of 는 null 키로 찾으면 NPE
        return KOREAN_AIRLINE_NAMES.getOrDefault(airline.iata(), airline.name());
    }

    private static Point point(Movement m) {
        if (m == null || m.airport() == null || m.airport().location() == null || m.scheduledTime() == null) return null;
        return new Point(m.airport().iata(), OffsetDateTime.parse(m.scheduledTime().local(), LOCAL_TIME).toLocalDateTime(),
                m.airport().location().lat(), m.airport().location().lon());
    }

    // AeroDataBox 응답 중 쓰는 칸만
    record Flight(String number, Airline airline, Movement departure, Movement arrival) {}
    record Airline(String name, String iata) {}
    record Movement(Airport airport, Time scheduledTime) {}
    record Airport(String iata, Location location) {}
    record Location(double lat, double lon) {}
    record Time(String local) {}
}
