package br.com.alicerce.seguranca;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/** Dá ao teste acesso ao conversor de papéis, que é de pacote. */
public final class ConfiguracaoDeSegurancaAcesso {

    private ConfiguracaoDeSegurancaAcesso() {
    }

    public static AbstractAuthenticationToken converter(Jwt jwt) {
        return ConfiguracaoDeSeguranca.papeisDoKeycloak().convert(jwt);
    }
}
