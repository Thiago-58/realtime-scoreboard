package br.com.scoreboard.exception;

public class PartidaNaoEncontradaException extends RuntimeException {

    // Esse é o construtor que estava faltando! Ele recebe a mensagem de erro.
    public PartidaNaoEncontradaException(String message) {
        super(message);
    }

}