package br.com.alicerce.saida;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Cliente HTTP para chamar URL que veio de fora, sem virar SSRF.
 *
 * <p>Use este cliente sempre que o destino depender de algo que o usuário
 * informou: webhook, importação por URL, prévia de link, foto por endereço.
 * Para integração com endereço fixo na configuração, o RestClient comum basta.
 *
 * <p>O que ele garante: só esquema e porta da política; nenhum endereço interno,
 * nem por nome que resolve para dentro, nem por redirecionamento; tempo e
 * tamanho de resposta limitados; nada de proxy do sistema.
 */
public class ClienteHttpSeguro implements Closeable {

    private final PoliticaDeSaida politica;
    private final CloseableHttpClient cliente;

    public ClienteHttpSeguro(PoliticaDeSaida politica) {
        this(politica, new ResolvedorSeguro(politica));
    }

    ClienteHttpSeguro(PoliticaDeSaida politica, ResolvedorSeguro resolvedor) {
        this.politica = politica;
        Timeout limite = Timeout.of(politica.tempoLimite());
        this.cliente = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(resolvedor)
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(limite)
                                .setSocketTimeout(limite)
                                .build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(limite)
                        .setMaxRedirects(politica.maxRedirecionamentos())
                        .setCircularRedirectsAllowed(false)
                        .build())
                .setRedirectStrategy(new RedirecionamentoConferido())
                .disableCookieManagement()
                .disableAuthCaching()
                .build();
    }

    /** Busca o recurso e devolve o corpo como texto, ou lança se a política recusar. */
    public String buscar(URI destino) throws IOException {
        conferir(destino);
        return cliente.execute(new HttpGet(destino), resposta -> {
            if (resposta.getCode() >= 400) {
                throw new IOException("o destino respondeu " + resposta.getCode());
            }
            if (resposta.getEntity() == null) {
                return "";
            }
            try (InputStream corpo = resposta.getEntity().getContent()) {
                return lerComLimite(corpo);
            }
        });
    }

    private String lerComLimite(InputStream corpo) throws IOException {
        ByteArrayOutputStream lido = new ByteArrayOutputStream();
        byte[] bloco = new byte[8192];
        long total = 0;
        int n;
        while ((n = corpo.read(bloco)) != -1) {
            total += n;
            // Para no meio: ler tudo para depois medir é o próprio ataque.
            if (total > politica.tamanhoMaximoEmBytes()) {
                throw new IOException("resposta maior que " + politica.tamanhoMaximoEmBytes() + " bytes");
            }
            lido.write(bloco, 0, n);
        }
        return lido.toString(StandardCharsets.UTF_8);
    }

    private void conferir(URI destino) throws DestinoBloqueado {
        String esquema = destino.getScheme() == null ? "" : destino.getScheme().toLowerCase(Locale.ROOT);
        if (!politica.esquemas().contains(esquema)) {
            throw new DestinoBloqueado("esquema não permitido: " + esquema);
        }
        if (destino.getHost() == null || destino.getRawUserInfo() != null) {
            throw new DestinoBloqueado("destino sem host ou com credencial embutida");
        }
        int porta = destino.getPort() != -1 ? destino.getPort() : "https".equals(esquema) ? 443 : 80;
        if (!politica.portas().contains(porta)) {
            throw new DestinoBloqueado("porta não permitida: " + porta);
        }
    }

    /** Aplica a mesma checagem de esquema e porta a cada salto; o endereço é do resolvedor. */
    private final class RedirecionamentoConferido extends DefaultRedirectStrategy {
        @Override
        public URI getLocationURI(HttpRequest pedido, HttpResponse resposta, HttpContext contexto)
                throws HttpException {
            URI proximo = super.getLocationURI(pedido, resposta, contexto);
            try {
                conferir(proximo);
            } catch (DestinoBloqueado e) {
                throw new ProtocolException("redirecionamento recusado: " + e.getMessage(), e);
            }
            return proximo;
        }
    }

    @Override
    public void close() throws IOException {
        cliente.close();
    }
}
