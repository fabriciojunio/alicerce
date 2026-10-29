package br.com.alicerce.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identificador de correlação em toda requisição, no log e na resposta.
 *
 * <p>Só aceita o que o cliente mandar se for um UUID. Qualquer outro texto
 * iria parar no log, e é por campo de log aberto que se injeta linha falsa.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroDeCorrelacao extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Correlation-Id";
    private static final Pattern UUID_VALIDO = Pattern.compile("^[0-9a-fA-F-]{36}$");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain cadeia)
            throws ServletException, IOException {
        String recebido = req.getHeader(CABECALHO);
        String id = recebido != null && UUID_VALIDO.matcher(recebido).matches() ? recebido : UUID.randomUUID().toString();
        MDC.put("correlationId", id);
        resp.setHeader(CABECALHO, id);
        try {
            cadeia.doFilter(req, resp);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
