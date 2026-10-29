package br.com.alicerce.saida;

import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.DnsResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClienteHttpSeguroTest {

    private HttpServer servidor;
    private int porta;

    @BeforeEach
    void subirServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/ok", troca -> responder(troca, 200, "ok"));
        servidor.createContext("/grande", troca -> responder(troca, 200, "x".repeat(5000)));
        servidor.createContext("/para-metadados", troca -> redirecionar(troca, "http://169.254.169.254/latest/meta-data/"));
        servidor.createContext("/para-arquivo", troca -> redirecionar(troca, "file:///etc/passwd"));
        servidor.createContext("/para-outra-porta", troca -> redirecionar(troca, "http://127.0.0.1:6379/"));
        servidor.start();
        porta = servidor.getAddress().getPort();
    }

    @AfterEach
    void derrubarServidor() {
        servidor.stop(0);
    }

    /** Política de teste: libera só este servidor local, nesta porta, por HTTP. */
    private PoliticaDeSaida politicaDoServidorLocal(long tamanhoMaximo) {
        return new PoliticaDeSaida(Set.of("http"), Set.of(porta), List.of(Rede.de("127.0.0.1/32")), 3,
                Duration.ofSeconds(5), tamanhoMaximo);
    }

    @ParameterizedTest(name = "{0} é recusado")
    @ValueSource(strings = {
            "127.0.0.1", "localhost", "2130706433", "10.0.0.5", "172.16.3.4", "192.168.1.1",
            "169.254.169.254", "100.64.0.1", "0.0.0.0", "224.0.0.1",
            "::1", "fd00::1", "fe80::1", "::ffff:127.0.0.1", "64:ff9b::7f00:1", "2002:7f00:1::1"})
    void enderecoInternoERecusadoNoResolvedor(String host) {
        ResolvedorSeguro resolvedor = new ResolvedorSeguro(PoliticaDeSaida.padrao());
        assertThatThrownBy(() -> resolvedor.resolve(host)).isInstanceOf(DestinoBloqueado.class);
    }

    @Test
    void enderecoPublicoPassa() throws Exception {
        DnsResolver falso = resolvedorFalso("93.184.216.34");
        ResolvedorSeguro resolvedor = new ResolvedorSeguro(PoliticaDeSaida.padrao(), falso);
        assertThat(resolvedor.resolve("exemplo.test")).extracting(InetAddress::getHostAddress)
                .containsExactly("93.184.216.34");
    }

    @Test
    void basteUmRegistroInternoParaRecusar() {
        // Nome com dois registros: um público, um interno. O cliente poderia escolher o segundo.
        DnsResolver falso = resolvedorFalso("93.184.216.34", "127.0.0.1");
        ResolvedorSeguro resolvedor = new ResolvedorSeguro(PoliticaDeSaida.padrao(), falso);
        assertThatThrownBy(() -> resolvedor.resolve("misto.test")).isInstanceOf(DestinoBloqueado.class);
    }

    @ParameterizedTest(name = "{0} é recusado antes de conectar")
    @ValueSource(strings = {
            "http://exemplo.com/", "file:///etc/passwd", "ftp://exemplo.com/", "gopher://exemplo.com/",
            "https://exemplo.com:8443/", "https://usuario:senha@exemplo.com/", "/caminho/relativo"})
    void esquemaPortaECredencialSaoConferidosAntes(String destino) throws IOException {
        try (ClienteHttpSeguro cliente = new ClienteHttpSeguro(PoliticaDeSaida.padrao())) {
            assertThatThrownBy(() -> cliente.buscar(URI.create(destino))).isInstanceOf(DestinoBloqueado.class);
        }
    }

    @Test
    void casoFelizDevolveOCorpo() throws IOException {
        try (ClienteHttpSeguro cliente = new ClienteHttpSeguro(politicaDoServidorLocal(1024))) {
            assertThat(cliente.buscar(URI.create("http://127.0.0.1:" + porta + "/ok"))).isEqualTo("ok");
        }
    }

    @Test
    void redirecionamentoParaMetadadosDaNuvemERecusado() throws IOException {
        try (ClienteHttpSeguro cliente = new ClienteHttpSeguro(politicaDoServidorLocal(1024))) {
            assertThatThrownBy(() -> cliente.buscar(URI.create("http://127.0.0.1:" + porta + "/para-metadados")))
                    .isInstanceOf(IOException.class)
                    .satisfies(e -> assertThat(causas(e)).anyMatch(DestinoBloqueado.class::isInstance));
        }
    }

    @Test
    void redirecionamentoParaArquivoERecusado() throws IOException {
        try (ClienteHttpSeguro cliente = new ClienteHttpSeguro(politicaDoServidorLocal(1024))) {
            assertThatThrownBy(() -> cliente.buscar(URI.create("http://127.0.0.1:" + porta + "/para-arquivo")))
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void redirecionamentoParaOutraPortaDoMesmoHostERecusado() throws IOException {
        // O host está liberado; a porta 6379 (Redis), não.
        try (ClienteHttpSeguro cliente = new ClienteHttpSeguro(politicaDoServidorLocal(1024))) {
            assertThatThrownBy(() -> cliente.buscar(URI.create("http://127.0.0.1:" + porta + "/para-outra-porta")))
                    .isInstanceOf(IOException.class)
                    .satisfies(e -> assertThat(causas(e)).anyMatch(DestinoBloqueado.class::isInstance));
        }
    }

    @Test
    void respostaMaiorQueOLimiteEInterrompida() throws IOException {
        try (ClienteHttpSeguro cliente = new ClienteHttpSeguro(politicaDoServidorLocal(1000))) {
            assertThatThrownBy(() -> cliente.buscar(URI.create("http://127.0.0.1:" + porta + "/grande")))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("resposta maior");
        }
    }

    @Test
    void redeSoAceitaEnderecoLiteral() {
        assertThatThrownBy(() -> Rede.de("intranet.empresa/24")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Rede.de("10.1.0.0/16").contem(endereco("10.1.200.3"))).isTrue();
        assertThat(Rede.de("10.1.0.0/16").contem(endereco("10.2.0.1"))).isFalse();
    }

    private static List<Throwable> causas(Throwable erro) {
        List<Throwable> lista = new java.util.ArrayList<>();
        for (Throwable atual = erro; atual != null && !lista.contains(atual); atual = atual.getCause()) {
            lista.add(atual);
        }
        return lista;
    }

    private static InetAddress endereco(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DnsResolver resolvedorFalso(String... enderecos) {
        return new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) {
                return java.util.Arrays.stream(enderecos).map(ClienteHttpSeguroTest::endereco)
                        .toArray(InetAddress[]::new);
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
    }

    private static void responder(com.sun.net.httpserver.HttpExchange troca, int status, String corpo)
            throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.sendResponseHeaders(status, bytes.length);
        try (OutputStream saida = troca.getResponseBody()) {
            saida.write(bytes);
        }
    }

    private static void redirecionar(com.sun.net.httpserver.HttpExchange troca, String destino) throws IOException {
        troca.getResponseHeaders().add("Location", destino);
        troca.sendResponseHeaders(302, -1);
        troca.close();
    }
}
