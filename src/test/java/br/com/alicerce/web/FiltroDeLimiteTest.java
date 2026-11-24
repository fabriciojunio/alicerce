package br.com.alicerce.web;

import br.com.alicerce.observabilidade.EventosDeSeguranca;
import br.com.alicerce.seguranca.PropriedadesDeSeguranca;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Memória e chave do limite de requisições")
class FiltroDeLimiteTest {

    private static final class RelogioDeTeste extends Clock {
        private Instant agora = Instant.parse("2026-11-24T12:00:00Z");

        void avancar(Duration tempo) {
            agora = agora.plus(tempo);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final RelogioDeTeste relogio = new RelogioDeTeste();
    private final FiltroDeLimite filtro = new FiltroDeLimite(
            new PropriedadesDeSeguranca(List.of(), 3), relogio, new EventosDeSeguranca(new SimpleMeterRegistry()));

    private static String ipv4(int n) {
        return "10." + (n >> 16 & 255) + "." + (n >> 8 & 255) + "." + (n & 255);
    }

    @Test
    void janelaVencidaSaiDaMemoriaNaViradaDoMinuto() {
        for (int i = 0; i < 1_000; i++) {
            filtro.permitir(ipv4(i));
        }
        assertThat(filtro.enderecosGuardados()).isEqualTo(1_000);
        relogio.avancar(Duration.ofMinutes(1));
        filtro.permitir("203.0.113.1");
        assertThat(filtro.enderecosGuardados()).isEqualTo(1);
    }

    @Test
    void trocarDeEnderecoNaoEncheAMemoria() {
        for (int i = 0; i < FiltroDeLimite.TETO_DE_ENDERECOS + 5_000; i++) {
            filtro.permitir(ipv4(i));
        }
        // O teto mais o balde compartilhado.
        assertThat(filtro.enderecosGuardados()).isLessThanOrEqualTo(FiltroDeLimite.TETO_DE_ENDERECOS + 1);
        // Quem já tinha balde continua com o dele.
        assertThat(filtro.permitir(ipv4(0))).isTrue();
    }

    @Test
    void trocarDeEnderecoDentroDoMesmoBlocoIpv6NaoDaBaldeNovo() {
        for (int i = 1; i <= 3; i++) {
            assertThat(filtro.permitir("2001:db8:1:2::" + i)).isTrue();
        }
        assertThat(filtro.permitir("2001:db8:1:2:ffff::9")).isFalse();
        assertThat(filtro.permitir("2001:db8:1:3::1")).isTrue();
    }

    @Test
    void ipv4MapeadoEmIpv6EOMesmoEndereco() {
        assertThat(FiltroDeLimite.origem("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(FiltroDeLimite.origem("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(FiltroDeLimite.origem("fe80::1%eth0")).endsWith("/64");
    }
}
