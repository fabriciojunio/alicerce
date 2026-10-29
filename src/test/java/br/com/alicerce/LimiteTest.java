package br.com.alicerce;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "alicerce.seguranca.requisicoes-por-minuto=3")
@AutoConfigureMockMvc
@DisplayName("Limite de requisições")
class LimiteTest {

    @Autowired
    MockMvc mvc;

    @Test
    void quartaRequisicaoDoMesmoEnderecoRecebe429() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/actuator/health").with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/actuator/health").with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
        // Outro endereço tem a própria janela.
        mvc.perform(get("/actuator/health").with(r -> { r.setRemoteAddr("203.0.113.8"); return r; }))
                .andExpect(status().isOk());
    }
}
