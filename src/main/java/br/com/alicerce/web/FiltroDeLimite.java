package br.com.alicerce.web;

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
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite de requisições por endereço, em janela de um minuto.
 *
 * <p>Fica na memória da instância: com várias réplicas, o limite efetivo
 * multiplica. Serve contra abuso de uma origem só; contra ataque distribuído
 * quem segura é o gateway ou o WAF na frente. Isso está escrito no README.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class FiltroDeLimite extends OncePerRequestFilter {

    private record Janela(long minuto, AtomicInteger contagem) { }

    private final int limite;
    private final Clock relogio;
    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    public FiltroDeLimite(PropriedadesDeSeguranca propriedades, Clock relogio) {
        this.limite = propriedades.requisicoesPorMinuto();
        this.relogio = relogio;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain cadeia)
            throws ServletException, IOException {
        long minuto = relogio.millis() / 60_000;
        // getRemoteAddr depois que o Tomcat resolveu os proxies confiáveis
        // (server.forward-headers-strategy): o X-Forwarded-For do cliente não
        // troca de balde.
        Janela janela = janelas.compute(req.getRemoteAddr(), (ip, atual) ->
                atual == null || atual.minuto() != minuto ? new Janela(minuto, new AtomicInteger()) : atual);
        if (janela.contagem().incrementAndGet() > limite) {
            resp.setStatus(429);
            resp.setHeader("Retry-After", "60");
            resp.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            resp.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\",\"detail\":\"Limite de requisições atingido\"}");
            return;
        }
        cadeia.doFilter(req, resp);
    }
}
