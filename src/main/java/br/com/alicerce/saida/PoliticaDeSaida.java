package br.com.alicerce.saida;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * O que uma chamada HTTP para fora pode alcançar.
 *
 * <p>O padrão é o mais fechado que ainda serve para integração com terceiro:
 * só HTTPS na porta 443, nenhum endereço interno, três redirecionamentos e
 * resposta de até 1 MB. Serviço interno que precise ser chamado entra em
 * {@code redesLiberadas} com a faixa exata, e isso fica visível na revisão.
 */
public record PoliticaDeSaida(
        Set<String> esquemas,
        Set<Integer> portas,
        List<Rede> redesLiberadas,
        int maxRedirecionamentos,
        Duration tempoLimite,
        long tamanhoMaximoEmBytes) {

    public PoliticaDeSaida {
        esquemas = Set.copyOf(esquemas);
        portas = Set.copyOf(portas);
        redesLiberadas = List.copyOf(redesLiberadas);
        if (esquemas.isEmpty() || portas.isEmpty()) {
            throw new IllegalArgumentException("a política precisa de ao menos um esquema e uma porta");
        }
    }

    public static PoliticaDeSaida padrao() {
        return new PoliticaDeSaida(Set.of("https"), Set.of(443), List.of(), 3,
                Duration.ofSeconds(10), 1024 * 1024);
    }

    boolean liberada(java.net.InetAddress endereco) {
        return redesLiberadas.stream().anyMatch(rede -> rede.contem(endereco));
    }
}
