package br.com.alicerce.observabilidade;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Contagem do que interessa à detecção, na métrica {@code alicerce_seguranca_eventos_total}.
 *
 * <p>O rótulo é uma enumeração fechada: valor vindo do cliente deixaria quem
 * ataca criar série sem limite e poria dado pessoal na métrica. Toda série nasce
 * em zero; sem isso ela só existe depois do primeiro evento, já valendo 1, e o
 * {@code increase()} do alerta perde justamente a primeira ocorrência.
 */
@Component
public class EventosDeSeguranca {

    public enum Evento {
        /** Token apresentado e recusado: assinatura, emissor, audiência ou prazo. */
        TOKEN_RECUSADO,
        /** Chamada sem credencial nenhuma. */
        SEM_CREDENCIAL,
        /** Autenticado, mas sem o papel da rota. */
        ACESSO_NEGADO,
        LIMITE_ATINGIDO,
        /** O cliente anti-SSRF recusou um destino. */
        DESTINO_BLOQUEADO;

        String rotulo() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final String METRICA = "alicerce.seguranca.eventos";

    private final Map<Evento, Counter> contadores = new EnumMap<>(Evento.class);

    public EventosDeSeguranca(MeterRegistry registro) {
        for (Evento evento : Evento.values()) {
            contadores.put(evento, Counter.builder(METRICA)
                    .description("Eventos de segurança do serviço")
                    .tag("evento", evento.rotulo())
                    .register(registro));
        }
    }

    public void registrar(Evento evento) {
        contadores.get(evento).increment();
    }

    @EventListener
    void aoRecusarToken(AbstractAuthenticationFailureEvent falha) {
        registrar(Evento.TOKEN_RECUSADO);
    }

    @EventListener
    void aoNegarAcesso(AuthorizationDeniedEvent<?> negado) {
        Authentication quem = negado.getAuthentication().get();
        boolean anonimo = quem == null || quem instanceof AnonymousAuthenticationToken;
        registrar(anonimo ? Evento.SEM_CREDENCIAL : Evento.ACESSO_NEGADO);
    }
}
