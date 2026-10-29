package br.com.alicerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Modelo de serviço seguro por padrão. Quem cria um serviço novo copia este
 * repositório e troca o pacote {@code pedidos} pelo próprio domínio; o resto
 * já vem configurado.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AlicerceAplicacao {

    public static void main(String[] args) {
        SpringApplication.run(AlicerceAplicacao.class, args);
    }
}
