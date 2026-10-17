package com.dev.nexusfinance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Expoe o relogio como dependencia injetavel.
 *
 * <p>Sem isto, {@code LocalDate.now()} e {@code Instant.now()} ficam embutidos
 * nos servicos e tornam intestaveis dois cenarios que importam: a virada de mes
 * no analytics e a expiracao de token na autenticacao. Em teste, injeta-se um
 * {@link Clock#fixed} e o tempo passa a ser um parametro.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
