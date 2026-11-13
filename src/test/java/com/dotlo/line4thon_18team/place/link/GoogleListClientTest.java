package com.dotlo.line4thon_18team.place.link;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.place.importer.ImportedList;
import com.dotlo.line4thon_18team.place.importer.ImportedPlace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 구글에 요청하지 않고, 저장해 둔 실제 응답(2026-10 공개 리스트, 개인정보는 가짜 값으로 바꿈)으로 확인한다.
 * 이 테스트가 깨지면 구글 응답 구조가 바뀐 것이 아니라 코드가 바뀐 것. 구조가 바뀌었는지는 샘플을 새로 받아 확인한다.
 */
class GoogleListClientTest {

    private final GoogleListClient client = new GoogleListClient();

    @Test
    void 공유_리스트_응답에서_리스트_이름과_장소를_읽는다() throws IOException {
        ImportedList list = client.parse(sample());

        assertThat(list.listName()).isEqualTo("4호선톤A");
        assertThat(list.places()).hasSize(8);
        assertThat(list.skippedRows()).isZero();

        ImportedPlace first = list.places().get(0);
        assertThat(first.name()).isEqualTo("한성대학교");
        assertThat(first.latitude()).isEqualTo(37.5823688);
        assertThat(first.longitude()).isEqualTo(127.0111299);
        assertThat(first.note()).isNull();
    }

    @Test
    void feature_ID를_Takeout과_같은_형식으로_바꾼다() throws IOException {
        ImportedList list = client.parse(sample());

        // 두 번째 숫자가 음수로 오는 장소. Takeout CSV 의 값과 같아야 업로드와 링크 사이 중복 처리가 된다
        ImportedPlace sookmyung = list.places().get(4);
        assertThat(sookmyung.name()).isEqualTo("숙명여자대학교");
        assertThat(sookmyung.featureId()).isEqualTo("0x357ca2727fcb04c7:0xb9f64e162324730f");
        assertThat(sookmyung.googleUrl()).isEqualTo("https://maps.google.com/?cid=13399983598273327887");

        ImportedPlace dongguk = list.places().get(7);
        assertThat(dongguk.featureId()).isEqualTo("0x357ca302d491f811:0x809ffb62a47609b5");
    }

    @Test
    void 비공개_리스트면_LIST_PRIVATE() {
        String privateList = ")]}'\n[null,null,[5,\"abc\"]]";

        assertThatThrownBy(() -> client.parse(privateList))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LIST_PRIVATE);
    }

    // 구글맵 링크가 아니면 구글에 요청하기 전에 막는다 (서버가 아무 주소로나 요청을 보내지 않게)
    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/maps/abc",
            "http://maps.app.goo.gl/abc",
            "https://www.google.com.evil.com/maps/abc",
            "https://169.254.169.254/maps",
            "https://www.google.com/search?q=abc",
            "maps.app.goo.gl/abc",
            "구글맵 링크"
    })
    void 구글맵_공유_링크가_아니면_INVALID_LIST_URL(String url) {
        assertThatThrownBy(() -> client.fetch(url))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_LIST_URL);
    }

    private String sample() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/google/shared-list-sample.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
