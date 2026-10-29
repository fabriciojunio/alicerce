package br.com.alicerce.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.NoSuchElementException;

/**
 * Erros no padrão Problem Details (RFC 9457). O cliente recebe status, uma
 * frase e o identificador de correlação; a pilha fica só no log.
 */
@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler({NoSuchElementException.class, NoResourceFoundException.class})
    ProblemDetail naoEncontrado() {
        return problema(HttpStatus.NOT_FOUND, "Recurso não encontrado");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class})
    ProblemDetail invalido() {
        return problema(HttpStatus.BAD_REQUEST, "Requisição inválida");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception e) {
        log.error("Erro inesperado", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno");
    }

    private static ProblemDetail problema(HttpStatus status, String detalhe) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detalhe);
        p.setProperty("correlationId", MDC.get("correlationId"));
        return p;
    }
}
