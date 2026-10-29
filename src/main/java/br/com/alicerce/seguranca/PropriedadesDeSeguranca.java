package br.com.alicerce.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuração de segurança vinda do ambiente.
 *
 * @param origensPermitidas  origens do CORS; vazio quer dizer nenhuma
 * @param requisicoesPorMinuto limite por endereço de origem
 */
@ConfigurationProperties(prefix = "alicerce.seguranca")
public record PropriedadesDeSeguranca(List<String> origensPermitidas, int requisicoesPorMinuto) {

    public PropriedadesDeSeguranca {
        origensPermitidas = origensPermitidas == null ? List.of() : List.copyOf(origensPermitidas);
        if (origensPermitidas.contains("*")) {
            throw new IllegalStateException("CORS com * não é aceito: liste as origens");
        }
        if (requisicoesPorMinuto <= 0) {
            requisicoesPorMinuto = 120;
        }
    }
}
