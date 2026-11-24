package br.com.alicerce.web;

import br.com.alicerce.observabilidade.EventosDeSeguranca;
import br.com.alicerce.observabilidade.EventosDeSeguranca.Evento;
import br.com.alicerce.seguranca.PropriedadesDeSeguranca;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Limite de requisições por endereço, em janela de um minuto.
 *
 * <p>Fica na memória da instância: com várias réplicas, o limite efetivo
 * multiplica. Serve contra abuso de uma origem só; contra ataque distribuído
 * quem segura é o gateway ou o WAF na frente. Isso está escrito no README.
 *
 * <p>A memória é limitada de propósito. Guardar um registro por endereço para
 * sempre faria do próprio limite a negação de serviço: bastaria trocar de IP.
 * Janela vencida sai na virada do minuto; passado o teto, endereço novo divide
 * um balde só.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class FiltroDeLimite extends OncePerRequestFilter {

    static final int TETO_DE_ENDERECOS = 50_000;
    private static final String EXCEDENTE = "excedente";

    private record Janela(long minuto, AtomicInteger contagem) { }

    private final int limite;
    private final Clock relogio;
    private final EventosDeSeguranca eventos;
    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();
    private final AtomicLong ultimaLimpeza = new AtomicLong();

    public FiltroDeLimite(PropriedadesDeSeguranca propriedades, Clock relogio, EventosDeSeguranca eventos) {
        this.limite = propriedades.requisicoesPorMinuto();
        this.relogio = relogio;
        this.eventos = eventos;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain cadeia)
            throws ServletException, IOException {
        // getRemoteAddr depois que o Tomcat resolveu os proxies confiáveis
        // (server.forward-headers-strategy): o X-Forwarded-For do cliente não
        // troca de balde.
        if (!permitir(req.getRemoteAddr())) {
            eventos.registrar(Evento.LIMITE_ATINGIDO);
            resp.setStatus(429);
            resp.setHeader("Retry-After", "60");
            resp.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            resp.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\",\"detail\":\"Limite de requisições atingido\"}");
            return;
        }
        cadeia.doFilter(req, resp);
    }

    boolean permitir(String endereco) {
        long minuto = relogio.millis() / 60_000;
        limparVencidas(minuto);
        String origem = origem(endereco);
        String balde = janelas.containsKey(origem) || janelas.size() < TETO_DE_ENDERECOS ? origem : EXCEDENTE;
        Janela janela = janelas.compute(balde, (chave, atual) ->
                atual == null || atual.minuto() != minuto ? new Janela(minuto, new AtomicInteger()) : atual);
        return janela.contagem().incrementAndGet() <= limite;
    }

    private void limparVencidas(long minuto) {
        long anterior = ultimaLimpeza.get();
        if (minuto > anterior && ultimaLimpeza.compareAndSet(anterior, minuto)) {
            janelas.values().removeIf(janela -> janela.minuto() < minuto);
        }
    }

    /**
     * Em IPv6 o balde é o /64: uma pessoa só costuma ter 2^64 endereços, e contar
     * um a um não limitaria nada.
     */
    static String origem(String endereco) {
        if (endereco == null || endereco.indexOf(':') < 0) {
            return String.valueOf(endereco);
        }
        try {
            // Literal com dois-pontos nunca consulta DNS.
            InetAddress ip = InetAddress.getByName(endereco.replaceFirst("%.*$", ""));
            return ip instanceof Inet6Address
                    ? HexFormat.of().formatHex(ip.getAddress(), 0, 8) + "/64"
                    : ip.getHostAddress();
        } catch (UnknownHostException e) {
            return EXCEDENTE;
        }
    }

    int enderecosGuardados() {
        return janelas.size();
    }
}
