package br.com.scoreboard.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auditoria")
public class RegistroAuditoria {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_acao", nullable = false, length = 30)
    private TipoAcao tipoAcao;

    @Column(name = "entidade_afetada", length = 50)
    private String entidadeAfetada;

    @Column(name = "id_entidade")
    private Long idEntidade;

    @Column(name = "valor_antes", columnDefinition = "text")
    private String valorAntes;

    @Column(name = "valor_depois", columnDefinition = "text")
    private String valorDepois;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    // Getters e Setters
    public Long getId() { return id; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public TipoAcao getTipoAcao() { return tipoAcao; }
    public void setTipoAcao(TipoAcao tipoAcao) { this.tipoAcao = tipoAcao; }
    public String getEntidadeAfetada() { return entidadeAfetada; }
    public void setEntidadeAfetada(String entidadeAfetada) { this.entidadeAfetada = entidadeAfetada; }
    public Long getIdEntidade() { return idEntidade; }
    public void setIdEntidade(Long idEntidade) { this.idEntidade = idEntidade; }
    public String getValorAntes() { return valorAntes; }
    public void setValorAntes(String valorAntes) { this.valorAntes = valorAntes; }
    public String getValorDepois() { return valorDepois; }
    public void setValorDepois(String valorDepois) { this.valorDepois = valorDepois; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
