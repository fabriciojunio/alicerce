package br.com.alicerce.saida;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * Resolve o nome e recusa o que aponta para dentro.
 *
 * <p>A checagem mora aqui, e não antes da chamada, por causa do DNS rebinding:
 * validar o nome e depois deixar o HttpClient resolver de novo abre uma janela
 * em que o mesmo nome passa a responder 127.0.0.1. Aqui o endereço validado é
 * o mesmo que a conexão usa. Cada redirecionamento passa por este mesmo ponto.
 */
public class ResolvedorSeguro implements DnsResolver {

    private static final List<Rede> INTERNAS = List.of(
            Rede.de("0.0.0.0/8"),
            Rede.de("10.0.0.0/8"),
            Rede.de("100.64.0.0/10"),
            Rede.de("127.0.0.0/8"),
            Rede.de("169.254.0.0/16"),
            Rede.de("172.16.0.0/12"),
            Rede.de("192.0.0.0/24"),
            Rede.de("192.168.0.0/16"),
            Rede.de("198.18.0.0/15"),
            Rede.de("224.0.0.0/4"),
            Rede.de("240.0.0.0/4"),
            Rede.de("::/128"),
            Rede.de("::1/128"),
            Rede.de("fc00::/7"),
            Rede.de("fe80::/10"),
            Rede.de("ff00::/8"),
            // NAT64 e 6to4 carregam um IPv4 dentro: 64:ff9b::7f00:1 é o 127.0.0.1.
            Rede.de("64:ff9b::/96"),
            Rede.de("2002::/16"));

    private final PoliticaDeSaida politica;
    private final DnsResolver sistema;

    public ResolvedorSeguro(PoliticaDeSaida politica) {
        this(politica, SystemDefaultDnsResolver.INSTANCE);
    }

    ResolvedorSeguro(PoliticaDeSaida politica, DnsResolver sistema) {
        this.politica = politica;
        this.sistema = sistema;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] enderecos = sistema.resolve(host);
        // Basta um endereço interno na resposta para recusar: com vários
        // registros, o cliente pode conectar em qualquer um deles.
        for (InetAddress endereco : enderecos) {
            if (interno(endereco) && !politica.liberada(endereco)) {
                throw new DestinoBloqueado("destino interno recusado: " + host);
            }
        }
        return enderecos;
    }

    @Override
    public List<InetSocketAddress> resolve(String host, int porta) throws UnknownHostException {
        return Arrays.stream(resolve(host)).map(e -> new InetSocketAddress(e, porta)).toList();
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        resolve(host);
        return sistema.resolveCanonicalHostname(host);
    }

    static boolean interno(InetAddress endereco) {
        if (endereco instanceof Inet6Address v6 && v6.isIPv4CompatibleAddress()) {
            return true;
        }
        return endereco.isAnyLocalAddress() || endereco.isLoopbackAddress() || endereco.isLinkLocalAddress()
                || endereco.isSiteLocalAddress() || endereco.isMulticastAddress()
                || (endereco instanceof Inet4Address || endereco instanceof Inet6Address)
                && INTERNAS.stream().anyMatch(rede -> rede.contem(endereco));
    }
}
