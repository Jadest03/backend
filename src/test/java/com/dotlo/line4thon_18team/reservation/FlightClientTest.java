package com.dotlo.line4thon_18team.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.dotlo.line4thon_18team.reservation.FlightClient.Airport;
import com.dotlo.line4thon_18team.reservation.FlightClient.Flight;
import com.dotlo.line4thon_18team.reservation.FlightClient.Location;
import com.dotlo.line4thon_18team.reservation.FlightClient.Movement;
import com.dotlo.line4thon_18team.reservation.FlightClient.Time;
import com.dotlo.line4thon_18team.reservation.dto.FlightInfo;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class FlightClientTest {

    // 실제 AeroDataBox 응답 (KE723 2026-11-15, 2026-11-08 조회)
    @Test
    void AeroDataBox_응답을_현지_시각과_공항_좌표로_바꾼다() throws Exception {
        List<Flight> raw;
        try (InputStream in = getClass().getResourceAsStream("/aerodatabox/KE723-2026-11-15.json")) {
            raw = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()
                    .readValue(in, new TypeReference<>() {});
        }

        List<FlightInfo> flights = FlightClient.toInfos(raw);

        assertThat(flights).hasSize(1);
        FlightInfo f = flights.get(0);
        assertThat(f.number()).isEqualTo("KE723");   // 응답은 "KE 723"
        assertThat(f.airline()).isEqualTo("Korean Air");
        assertThat(f.departure().airport()).isEqualTo("ICN");
        assertThat(f.departure().time()).isEqualTo(LocalDateTime.of(2026, 11, 15, 8, 50));   // 인천 현지
        assertThat(f.arrival().airport()).isEqualTo("KIX");
        assertThat(f.arrival().time()).isEqualTo(LocalDateTime.of(2026, 11, 15, 10, 45));    // 간사이 현지
        assertThat(f.arrival().lat()).isEqualTo(34.4273);
        assertThat(f.arrival().lng()).isEqualTo(135.244);
    }

    @Test
    void 공항_좌표나_시각이_빠진_항공편은_뺀다() {
        Movement full = new Movement(new Airport("ICN", new Location(37.4, 126.4)), new Time("2026-11-15 08:50+09:00"));
        Movement noLocation = new Movement(new Airport("KIX", null), new Time("2026-11-15 10:45+09:00"));
        Movement noTime = new Movement(new Airport("KIX", new Location(34.4, 135.2)), null);

        assertThat(FlightClient.toInfos(List.of(
                new Flight("KE 723", null, full, noLocation),
                new Flight("KE 723", null, full, noTime),
                new Flight("KE 723", null, full, null)))).isEmpty();
        assertThat(FlightClient.toInfos(null)).isEmpty();   // 못 찾으면 204 (본문 없음)
    }

    // 실제 API 호출. 키가 환경변수에 있을 때만 (CI에서는 건너뜀). 2유닛 사용
    @Test
    @EnabledIfEnvironmentVariable(named = "AERODATABOX_API_KEY", matches = ".+")
    void 실제로_편명과_날짜로_찾는다() {
        FlightClient client = new FlightClient(System.getenv("AERODATABOX_API_KEY"));

        List<FlightInfo> flights = client.find("KE723", LocalDate.now().plusDays(7));

        assertThat(flights).isNotEmpty();
        assertThat(flights.get(0).departure().airport()).isEqualTo("ICN");
        assertThat(flights.get(0).arrival().airport()).isEqualTo("KIX");
        System.out.println("KE723: " + flights);
    }
}
