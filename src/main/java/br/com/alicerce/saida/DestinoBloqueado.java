package br.com.alicerce.saida;

import java.net.UnknownHostException;

/**
 * O destino resolveu para um endereço que a política não deixa alcançar.
 *
 * <p>Estende {@link UnknownHostException} porque é o que o resolvedor de DNS do
 * HttpClient pode lançar: assim a conexão nem é aberta.
 */
public class DestinoBloqueado extends UnknownHostException {

    public DestinoBloqueado(String motivo) {
        super(motivo);
    }
}
