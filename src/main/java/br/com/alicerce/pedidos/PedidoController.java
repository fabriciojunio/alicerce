package br.com.alicerce.pedidos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exemplo de API com dono. Os três padrões que o modelo quer deixar prontos:
 *
 * <ul>
 *   <li>o dono sai do token, nunca da URL nem do corpo;</li>
 *   <li>recurso de outro dono responde 404, e não 403, para não confirmar que existe;</li>
 *   <li>o corpo da requisição é um record só com os campos que o cliente pode mandar.</li>
 * </ul>
 */
@RestController
public class PedidoController {

    private final Map<UUID, Pedido> pedidos = new ConcurrentHashMap<>();
    private final Clock relogio;

    public PedidoController(Clock relogio) {
        this.relogio = relogio;
    }

    public record NovoPedido(
            @NotBlank @Size(max = 200) String descricao,
            @NotNull @Positive @DecimalMax("100000.00") BigDecimal valor) {
    }

    @PostMapping("/api/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    public Pedido criar(@AuthenticationPrincipal Jwt token, @Valid @RequestBody NovoPedido novo) {
        var pedido = new Pedido(UUID.randomUUID(), token.getSubject(), novo.descricao(), novo.valor(), relogio.instant());
        pedidos.put(pedido.id(), pedido);
        return pedido;
    }

    @GetMapping("/api/pedidos")
    public List<Pedido> meus(@AuthenticationPrincipal Jwt token) {
        return pedidos.values().stream().filter(p -> p.dono().equals(token.getSubject())).toList();
    }

    @GetMapping("/api/pedidos/{id}")
    public Pedido um(@AuthenticationPrincipal Jwt token, @PathVariable UUID id) {
        Pedido pedido = pedidos.get(id);
        if (pedido == null || !pedido.dono().equals(token.getSubject())) {
            throw new NoSuchElementException();
        }
        return pedido;
    }

    /** Só para o papel admin; a regra fica na configuração de segurança. */
    @GetMapping("/api/admin/pedidos")
    public Collection<Pedido> todos() {
        return pedidos.values();
    }
}
