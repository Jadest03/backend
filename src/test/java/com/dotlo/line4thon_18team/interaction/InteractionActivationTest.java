package com.dotlo.line4thon_18team.interaction;

import com.dotlo.line4thon_18team.interaction.port.InteractionAccess;
import com.dotlo.line4thon_18team.interaction.service.ItemInteractionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class InteractionActivationTest {
    @Autowired ApplicationContext context;

    @Test
    void 실제_권한_연결_전에는_허용_대역이나_서비스가_자동으로_등록되지_않는다() {
        assertThat(context.getBeansOfType(InteractionAccess.class)).isEmpty();
        assertThat(context.getBeansOfType(ItemInteractionService.class)).isEmpty();
    }
}
