package br.com.scoreboard.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * TRATAMENTO GLOBAL DE ERROS: GlobalExceptionMapper
 * PACOTE: br.com.scoreboard.exception
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Intercepta todas as exceções lançadas nos endpoints REST da aplicação e
 * converte em respostas HTTP padronizadas com payload JSON no formato:
 * { "erro": "Mensagem descritiva do problema" }
 * 
 * MAPEAMENTO DE CÓDIGOS HTTP:
 * - 400 Bad Request: Dados inválidos (Bean Validation, campos com tipo errado, JSON quebrado).
 * - 404 Not Found: Partida não encontrada para o ID informado.
 * - 422 Unprocessable Entity: Tentativa de alterar placar em jogo encerrado ou no intervalo.
 * - 500 Internal Server Error: Erros inesperados de infraestrutura (banco, etc.).
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger log = Logger.getLogger(GlobalExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable ex) {
        // 404: Partida não encontrada
        if (ex instanceof PartidaNaoEncontradaException e) {
            return formatarErro(404, e.getMessage());
        }

        // 400 ou 422: Regras de negócio violadas (ex: jogo encerrado)
        if (ex instanceof RegraNegocioException e) {
            return formatarErro(e.getStatus(), e.getMessage());
        }

        // 400: Validações de anotações Bean Validation (@NotBlank, @Min, @Size)
        if (ex instanceof ConstraintViolationException e) {
            String msg = e.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            return formatarErro(400, "Dados inválidos: " + msg);
        }

        // 400: Formato de campo inválido (ex: passou string em campo numérico)
        if (ex instanceof InvalidFormatException e) {
            String campo = e.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "?")
                    .collect(Collectors.joining("."));
            return formatarErro(400, "Campo '" + campo + "' inválido: informe um valor do tipo esperado");
        }

        // 400: Erro de parse no JSON recebido
        if (ex instanceof JsonProcessingException || ex instanceof ProcessingException) {
            log.log(Level.SEVERE, "Erro de JSON na requisição: " + ex.getMessage(), ex);
            String detalhe = ex.getMessage();
            if (ex.getCause() != null) {
                detalhe += " | Causa: " + ex.getCause().getMessage();
            }
            return formatarErro(400, "Corpo da requisição inválido: " + detalhe);
        }

        // Exceções nativas do JAX-RS
        if (ex instanceof WebApplicationException we) {
            return we.getResponse();
        }

        // 500: Erros não previstos
        log.log(Level.SEVERE, "Erro inesperado no servidor", ex);
        return formatarErro(500, "Erro interno no servidor");
    }

    /**
     * Auxiliar para montar a resposta Response com status e corpo JSON uniforme.
     */
    private Response formatarErro(int status, String mensagem) {
        return Response.status(status)
                .entity(Map.of("erro", mensagem))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}