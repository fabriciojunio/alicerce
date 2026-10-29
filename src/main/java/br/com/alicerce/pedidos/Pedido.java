package br.com.alicerce.pedidos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Exemplo de recurso com dono. O dono é o {@code sub} do token, nunca um campo do corpo. */
public record Pedido(UUID id, String dono, String descricao, BigDecimal valor, Instant criadoEm) {
}
