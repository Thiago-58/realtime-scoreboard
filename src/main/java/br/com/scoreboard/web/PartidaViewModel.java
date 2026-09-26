package br.com.scoreboard.web;

import br.com.scoreboard.dto.PartidaResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ============================================================================
 * VIEW MODEL: PartidaViewModel
 * PACOTE: br.com.scoreboard.web
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Modelo visual (ViewModel) utilizado pela interface web Apache Wicket para
 * renderizar cada partida na tela.
 * 
 * POR QUE USAR VIEWMODEL?
 * - Implementa Serializable: obrigatório pelo ciclo de vida do Apache Wicket.
 * - Unifica os dados persistidos do PostgreSQL com os dados ao vivo do Redis.
 * - Fornece métodos utilitários de exibição (ex: getDataHoraFormatada).
 * - Indica visualmente para o usuário se o placar está vindo do Redis (aoVivo=true)
 *   ou do banco relacional (aoVivo=false).
 */
public class PartidaViewModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String timeA;
    private String timeB;
    private String status;
    private int placarA;
    private int placarB;
    private boolean aoVivo;
    private String dataHoraPartida;

    /**
     * Cria o ViewModel unindo dados do banco com o placar ao vivo armazenado no Redis.
     * 
     * @param p         Dados cadastrais da partida vindos do PostgreSQL
     * @param jsonCache JSON com placarA e placarB lido do Redis
     */
    public static PartidaViewModel aoVivo(PartidaResponse p, String jsonCache) {
        PartidaViewModel vm = doBanco(p);
        vm.aoVivo = true;
        try {
            JsonNode node = new ObjectMapper().readTree(jsonCache);
            vm.placarA = node.get("placarA").asInt();
            vm.placarB = node.get("placarB").asInt();
        } catch (Exception e) {
            // Se o JSON do Redis estiver inválido, desativa flag e usa valor do banco
            vm.aoVivo = false;
        }
        return vm;
    }

    /**
     * Cria o ViewModel exclusivamente a partir dos dados do banco relacional PostgreSQL.
     * 
     * @param p Dados da partida do PostgreSQL
     */
    public static PartidaViewModel doBanco(PartidaResponse p) {
        PartidaViewModel vm = new PartidaViewModel();
        vm.id = p.getId();
        vm.timeA = p.getTimeA();
        vm.timeB = p.getTimeB();
        vm.placarA = p.getPlacarA();
        vm.placarB = p.getPlacarB();
        vm.status = p.getStatus();
        vm.dataHoraPartida = p.getDataHoraPartida();
        vm.aoVivo = false;
        return vm;
    }

    /**
     * Retorna a data e hora formatada em padrão brasileiro (dd/MM/yyyy HH:mm)
     * para exibição legível no card da partida.
     */
    public String getDataHoraFormatada() {
        if (dataHoraPartida == null || dataHoraPartida.isBlank()) return "";
        try {
            LocalDateTime dt = LocalDateTime.parse(dataHoraPartida);
            return dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception e) {
            return dataHoraPartida;
        }
    }

    // Getters e Setters
    public boolean isAoVivo() { return aoVivo; }
    public Long getId() { return id; }
    public String getTimeA() { return timeA; }
    public String getTimeB() { return timeB; }
    public String getStatus() { return status; }
    public int getPlacarA() { return placarA; }
    public int getPlacarB() { return placarB; }
    public void setPlacarA(int placarA) { this.placarA = placarA; }
    public void setPlacarB(int placarB) { this.placarB = placarB; }
    public String getDataHoraPartida() { return dataHoraPartida; }
}