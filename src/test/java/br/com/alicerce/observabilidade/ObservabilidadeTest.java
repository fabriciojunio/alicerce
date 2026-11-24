package br.com.alicerce.observabilidade;

import br.com.alicerce.observabilidade.EventosDeSeguranca.Evento;
import br.com.alicerce.saida.ClienteHttpSeguro;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "alicerce.seguranca.requisicoes-por-minuto=1000")
@AutoConfigureMockMvc
@DisplayName("Observabilidade e detecção")
class ObservabilidadeTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MeterRegistry registro;

    @Autowired
    ClienteHttpSeguro cliente;

    private double contagem(Evento evento) {
        return registro.get(EventosDeSeguranca.METRICA).tag("evento", evento.rotulo()).counter().count();
    }

    private static RequestPostProcessor com(String papel) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + papel));
    }

    private String coleta() throws Exception {
        return mvc.perform(get("/actuator/prometheus").with(com("metricas")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void metricaExigeOPapelDeColeta() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").with(com("admin"))).andExpect(status().isForbidden());
        assertThat(coleta()).contains("alicerce_seguranca_eventos_total");
    }

    @Test
    void oRestoDoActuatorContinuaFechadoAteParaQuemColeta() throws Exception {
        for (String rota : new String[]{"env", "heapdump", "loggers", "metrics", "configprops"}) {
            mvc.perform(get("/actuator/" + rota).with(com("metricas"))).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest
    @EnumSource(Evento.class)
    void todaSerieNasceEmZeroSenaoOAlertaPerdeAPrimeiraVez(Evento evento) {
        assertThat(registro.find(EventosDeSeguranca.METRICA).tag("evento", evento.rotulo()).counter()).isNotNull();
    }

    @Test
    void chamadaSemCredencialETokenInvalidoContamSeparado() throws Exception {
        double sem = contagem(Evento.SEM_CREDENCIAL);
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
        assertThat(contagem(Evento.SEM_CREDENCIAL)).isEqualTo(sem + 1);

        double recusado = contagem(Evento.TOKEN_RECUSADO);
        mvc.perform(get("/api/pedidos").header("Authorization", "Bearer nao.e.token")).andExpect(status().isUnauthorized());
        assertThat(contagem(Evento.TOKEN_RECUSADO)).isEqualTo(recusado + 1);
        assertThat(contagem(Evento.SEM_CREDENCIAL)).isEqualTo(sem + 1);
    }

    @Test
    void papelInsuficienteConta() throws Exception {
        double antes = contagem(Evento.ACESSO_NEGADO);
        mvc.perform(get("/api/admin/pedidos").with(com("comum"))).andExpect(status().isForbidden());
        assertThat(contagem(Evento.ACESSO_NEGADO)).isEqualTo(antes + 1);
    }

    @Test
    void tentativaDeSsrfConta() {
        double antes = contagem(Evento.DESTINO_BLOQUEADO);
        assertThatThrownBy(() -> cliente.buscar(URI.create("https://169.254.169.254/latest/meta-data/")))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> cliente.buscar(URI.create("file:///etc/passwd"))).isInstanceOf(IOException.class);
        assertThat(contagem(Evento.DESTINO_BLOQUEADO)).isEqualTo(antes + 2);
    }

    @Test
    void rotaVaiPeloModeloENaoPeloCaminho() throws Exception {
        mvc.perform(get("/api/pedidos/11111111-2222-3333-4444-555555555555").with(com("comum")));
        mvc.perform(get("/wp-admin/setup.php").with(com("comum")));
        assertThat(coleta()).doesNotContain("11111111-2222").doesNotContain("wp-admin");
    }

    @Test
    void todoEventoDasRegrasExisteNaMetrica() throws Exception {
        String regras = Files.readString(Path.of("infra/observabilidade/regras.yml"));
        var citados = Pattern.compile("evento=\"([a-z_]+)\"").matcher(regras).results().map(m -> m.group(1)).distinct().toList();
        assertThat(citados).isNotEmpty();
        String coletado = coleta();
        assertThat(citados).allSatisfy(e -> assertThat(coletado).contains("evento=\"" + e + "\""));
    }

    @Test
    void todoAlertaApontaParaSecaoQueExisteNoRunbook() throws IOException {
        String regras = Files.readString(Path.of("infra/observabilidade/regras.yml"));
        String runbook = Files.readString(Path.of("docs/processo/operacao.md"));
        var ancoras = Pattern.compile("operacao\\.md#([\\w-]+)").matcher(regras).results().map(m -> m.group(1)).toList();
        assertThat(ancoras).hasSizeGreaterThan(4);
        assertThat(ancoras).allSatisfy(a -> assertThat(runbook).containsPattern("(?m)^### " + a.replace('-', ' ') + "$"));
    }
}
