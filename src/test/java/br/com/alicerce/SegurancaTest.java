package br.com.alicerce;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O que o modelo promete, provado. Se um serviço copiado daqui mudar alguma
 * destas garantias, o teste dele quebra junto.
 */
@SpringBootTest(properties = {
        "alicerce.seguranca.origens-permitidas=https://app.exemplo.com.br",
        "alicerce.seguranca.requisicoes-por-minuto=1000"})
@AutoConfigureMockMvc
@DisplayName("Garantias de segurança do modelo")
class SegurancaTest {

    @Autowired
    MockMvc mvc;

    static JwtRequestPostProcessor usuario(String sub, String... papeis) {
        return jwt().jwt(j -> j.subject(sub).claim("realm_access", Map.of("roles", List.of(papeis))))
                .authorities(java.util.Arrays.stream(papeis)
                        .<org.springframework.security.core.GrantedAuthority>map(
                                p -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + p))
                        .toList());
    }

    @Nested
    @DisplayName("autenticação e autorização")
    class Autorizacao {

        @Test
        void semTokenRecebe401() throws Exception {
            mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
        }

        @Test
        void rotaDeAdminRecusaUsuarioComum() throws Exception {
            mvc.perform(get("/api/admin/pedidos").with(usuario("ana"))).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/pedidos").with(usuario("op", "admin"))).andExpect(status().isOk());
        }

        @Test
        void pedidoDeOutroDonoResponde404() throws Exception {
            MvcResult criado = mvc.perform(post("/api/pedidos").with(usuario("ana"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"descricao\":\"Dados simulados\",\"valor\":10}"))
                    .andExpect(status().isCreated()).andReturn();
            String id = com.jayway.jsonpath.JsonPath.read(criado.getResponse().getContentAsString(), "$.id");

            mvc.perform(get("/api/pedidos/" + id).with(usuario("ana"))).andExpect(status().isOk());
            mvc.perform(get("/api/pedidos/" + id).with(usuario("bruno"))).andExpect(status().isNotFound());
        }

        @Test
        void donoVemDoTokenENaoDoCorpo() throws Exception {
            mvc.perform(post("/api/pedidos").with(usuario("ana"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"descricao\":\"x\",\"valor\":10,\"dono\":\"bruno\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.dono").value("ana"));
        }

        @Test
        void conversorLePapeisDoKeycloak() {
            var token = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t").header("alg", "none")
                    .subject("op").claim("realm_access", Map.of("roles", List.of("admin"))).build();
            var auth = br.com.alicerce.seguranca.ConfiguracaoDeSegurancaAcesso.converter(token);
            assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_admin");
        }
    }

    @Nested
    @DisplayName("entrada e erro")
    class Entrada {

        @Test
        void corpoInvalidoResponde400SemDetalheInterno() throws Exception {
            mvc.perform(post("/api/pedidos").with(usuario("ana"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"descricao\":\"\",\"valor\":-5}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Requisição inválida"))
                    .andExpect(jsonPath("$.trace").doesNotExist());
        }

        @Test
        void errosLevamOCorrelationId() throws Exception {
            String id = "3f2b6c1e-9a4d-4b7e-8c2f-1a2b3c4d5e6f";
            mvc.perform(get("/api/pedidos/00000000-0000-0000-0000-000000000000").with(usuario("ana"))
                            .header("X-Correlation-Id", id))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string("X-Correlation-Id", id))
                    .andExpect(jsonPath("$.correlationId").value(id));
        }

        @Test
        void correlationIdForaDoFormatoEhTrocado() throws Exception {
            MvcResult r = mvc.perform(get("/actuator/health").header("X-Correlation-Id", "x\nINFO falso")).andReturn();
            assertThat(r.getResponse().getHeader("X-Correlation-Id")).matches("[0-9a-f-]{36}");
        }
    }

    @Nested
    @DisplayName("cabeçalhos, CORS e actuator")
    class Borda {

        @Test
        void cabecalhosDeSegurancaEmTodaResposta() throws Exception {
            mvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"))
                    .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"));
        }

        @Test
        void corsSoParaOrigemDaLista() throws Exception {
            mvc.perform(options("/api/pedidos").header("Origin", "https://app.exemplo.com.br")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(header().string("Access-Control-Allow-Origin", "https://app.exemplo.com.br"));
            mvc.perform(options("/api/pedidos").header("Origin", "https://atacante.test")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void actuatorSoExpoeSaudeSemDetalhe() throws Exception {
            mvc.perform(get("/actuator/health")).andExpect(jsonPath("$.components").doesNotExist());
            mvc.perform(get("/actuator/env").with(usuario("op", "admin"))).andExpect(status().isForbidden());
            mvc.perform(get("/actuator/heapdump").with(usuario("op", "admin"))).andExpect(status().isForbidden());
        }
    }
}
