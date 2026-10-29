package br.com.alicerce.saida;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Deixa o cliente anti-SSRF pronto para injeção, com a política mais fechada.
 *
 * <p>Serviço que precise chamar uma rede interna declara o próprio bean de
 * {@link PoliticaDeSaida} com a faixa liberada; o padrão não libera nenhuma.
 */
@Configuration
public class ConfiguracaoDeSaida {

    @Bean
    PoliticaDeSaida politicaDeSaida() {
        return PoliticaDeSaida.padrao();
    }

    @Bean(destroyMethod = "close")
    ClienteHttpSeguro clienteHttpSeguro(PoliticaDeSaida politica) {
        return new ClienteHttpSeguro(politica);
    }
}
