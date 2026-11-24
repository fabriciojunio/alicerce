package br.com.alicerce.observabilidade;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;

@Configuration
public class ConfiguracaoDeObservabilidade {

    /** Sem este bean o Spring Security nega em silêncio: o 403 não vira evento. */
    @Bean
    AuthorizationEventPublisher publicadorDeAutorizacao(ApplicationEventPublisher publicador) {
        return new SpringAuthorizationEventPublisher(publicador);
    }
}
