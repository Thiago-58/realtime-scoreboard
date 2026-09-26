package br.com.scoreboard.dto;

import br.com.scoreboard.domain.RegistroAuditoria;

public class AuditoriaResponse {
    private Long id;
    private String usuario, tipoAcao, entidadeAfetada;
    private Long idEntidade;
    private String valorAntes, valorDepois, timestamp;

    public static AuditoriaResponse de(RegistroAuditoria r) {
        AuditoriaResponse resp = new AuditoriaResponse();
        resp.id = r.getId();
        resp.usuario = r.getUsuario();
        resp.tipoAcao = r.getTipoAcao().name();
        resp.entidadeAfetada = r.getEntidadeAfetada();
        resp.idEntidade = r.getIdEntidade();
        resp.valorAntes = r.getValorAntes();
        resp.valorDepois = r.getValorDepois();
        resp.timestamp = r.getTimestamp() != null ? r.getTimestamp().toString() : null;
        return resp;
    }

    // Getters
    public Long getId() { return id; }
    public String getUsuario() { return usuario; }
    public String getTipoAcao() { return tipoAcao; }
    public String getEntidadeAfetada() { return entidadeAfetada; }
    public Long getIdEntidade() { return idEntidade; }
    public String getValorAntes() { return valorAntes; }
    public String getValorDepois() { return valorDepois; }
    public String getTimestamp() { return timestamp; }
}
