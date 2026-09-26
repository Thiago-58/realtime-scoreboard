package br.com.scoreboard.exception;

public class RegraNegocioException extends RuntimeException {
    private final int status;

    public RegraNegocioException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() { return status; }
}