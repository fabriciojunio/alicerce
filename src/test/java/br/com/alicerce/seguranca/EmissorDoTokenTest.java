package br.com.alicerce.seguranca;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * No compose, o navegador fala com o Keycloak em localhost:8180 e o serviço em
 * keycloak:8080. O token sai com o endereço público no "iss"; as chaves vêm
 * pela rede interna. Este teste reproduz essa situação com um JWKS local num
 * endereço diferente do emissor.
 *
 * <p>Antes, o emissor configurado no compose era o interno, e todo token real
 * era recusado por "iss" diferente.
 */
@SpringBootTest(properties = "alicerce.seguranca.requisicoes-por-minuto=1000")
@AutoConfigureMockMvc
@DisplayName("Emissor público e chaves pela rede interna")
class EmissorDoTokenTest {

    static final String EMISSOR_PUBLICO = "http://localhost:8180/realms/alicerce";
    static final RSAKey CHAVE = gerarChave();
    static final HttpServer JWKS = subirJwks();

    @Autowired
    MockMvc mvc;

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> EMISSOR_PUBLICO);
        registro.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/certs");
        registro.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> "alicerce-api");
    }

    @AfterAll
    static void derrubar() {
        JWKS.stop(0);
    }

    @Test
    void tokenComOEmissorPublicoEAceito() throws Exception {
        mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + token(EMISSOR_PUBLICO)))
                .andExpect(status().isOk());
    }

    @Test
    void tokenComOutroEmissorERecusadoMesmoAssinadoPelaChaveCerta() throws Exception {
        mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + token("http://keycloak:8080/realms/alicerce")))
                .andExpect(status().isUnauthorized());
    }

    static String token(String emissor) throws Exception {
        Instant agora = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .issuer(emissor)
                .subject("ana")
                .audience("alicerce-api")
                .issueTime(Date.from(agora))
                .expirationTime(Date.from(agora.plusSeconds(300)))
                .build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(CHAVE.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(CHAVE));
        return jwt.serialize();
    }

    static RSAKey gerarChave() {
        try {
            return new RSAKeyGenerator(2048).keyID("chave-do-keycloak").generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static HttpServer subirJwks() {
        try {
            var servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] corpo = new JWKSet(CHAVE.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            servidor.createContext("/certs", troca -> {
                troca.getResponseHeaders().add("Content-Type", "application/json");
                troca.sendResponseHeaders(200, corpo.length);
                troca.getResponseBody().write(corpo);
                troca.close();
            });
            servidor.start();
            return servidor;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
