package br.com.alicerce.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ConfiguracaoDeTempo {

    /** Injetado para o teste do limite controlar a janela de tempo. */
    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }
}
