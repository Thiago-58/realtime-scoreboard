package br.com.scoreboard.web;

import br.com.scoreboard.cache.RedisCacheService;
import br.com.scoreboard.domain.StatusPartida;
import br.com.scoreboard.dto.CriarPartidaRequest;
import br.com.scoreboard.dto.PartidaResponse;
import br.com.scoreboard.service.PartidaService;
import jakarta.enterprise.inject.spi.CDI;
import org.apache.wicket.ajax.AjaxSelfUpdatingTimerBehavior;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 * CLASSE: HomePage
 * PACOTE: br.com.scoreboard.web
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Controlador da interface gráfica principal desenvolvida em Apache Wicket 10.
 * 
 * COMO FUNCIONA A ATUALIZAÇÃO EM TEMPO REAL SILENCIOSA:
 * - O formulário de criação de jogos fica isolado fora do container de partidas.
 * - O container de partidas possui um timer AJAX (AjaxSelfUpdatingTimerBehavior)
 *   que recarrega apenas a seção de partidas a cada 3 segundos via DOM parcial.
 * - Isso garante que, se o usuário estiver digitando o nome de um time no formulário,
 *   o texto NUNCA é apagado e o foco não é perdido.
 * - Na hora de exibir cada jogo, o sistema consulta primeiro o RedisCacheService.
 *   Se houver dado ao vivo no Redis, exibe com badge '⚡ AO VIVO (Redis)'.
 *   Se o Redis não tiver, exibe '💾 Banco (PostgreSQL)' de forma transparente (Cache-Aside).
 */
public class HomePage extends WebPage {
    private static final long serialVersionUID = 1L;

    // Estado do filtro selecionado (TODOS, EM_ANDAMENTO ou ENCERRADO)
    private String filtroStatus = "TODOS";

    // Modelos para os campos de entrada do formulário de criação
    private final IModel<String> timeAModel = Model.of("");
    private final IModel<String> timeBModel = Model.of("");
    private final IModel<String> placarAModel = Model.of("0");
    private final IModel<String> placarBModel = Model.of("0");

    public HomePage() {
        // Painel para exibição de mensagens de sucesso ou validação de erro
        add(new FeedbackPanel("feedback"));

        // ====================================================================
        // BLOCO 1: Formulário de Criação de Partida
        // ====================================================================
        Form<Void> formCriar = new Form<Void>("formCriar") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit() {
                String timeA = timeAModel.getObject();
                String timeB = timeBModel.getObject();
                String pAStr = placarAModel.getObject();
                String pBStr = placarBModel.getObject();

                // Validação de preenchimento obrigatório
                if (timeA == null || timeA.trim().isEmpty() || timeB == null || timeB.trim().isEmpty()) {
                    error("Preencha o nome dos dois times.");
                    return;
                }

                // Regra de negócio: times não podem ser iguais
                if (timeA.trim().equalsIgnoreCase(timeB.trim())) {
                    error("Os times da partida não podem ser iguais.");
                    return;
                }

                // Validação de placares inteiros positivos
                int placarA = 0;
                int placarB = 0;
                try {
                    if (pAStr != null && !pAStr.trim().isEmpty()) placarA = Integer.parseInt(pAStr.trim());
                    if (pBStr != null && !pBStr.trim().isEmpty()) placarB = Integer.parseInt(pBStr.trim());
                    if (placarA < 0 || placarB < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    error("Os placares devem ser números maiores ou iguais a zero.");
                    return;
                }

                try {
                    CriarPartidaRequest req = new CriarPartidaRequest();
                    req.setTimeA(timeA.trim());
                    req.setTimeB(timeB.trim());
                    req.setPlacarA(placarA);
                    req.setPlacarB(placarB);
                    req.setDataHoraPartida(LocalDateTime.now());

                    getPartidaService().criar(req, "painel-web");

                    success("Partida cadastrada com sucesso: " + timeA + " " + placarA + " x " + placarB + " " + timeB);
                    
                    // Limpa os campos após o cadastro com sucesso
                    timeAModel.setObject("");
                    timeBModel.setObject("");
                    placarAModel.setObject("0");
                    placarBModel.setObject("0");
                } catch (Exception e) {
                    error("Erro ao criar partida: " + e.getMessage());
                }
            }
        };

        formCriar.add(new TextField<>("novoTimeA", timeAModel).setRequired(true));
        formCriar.add(new TextField<>("novoPlacarA", placarAModel));
        formCriar.add(new TextField<>("novoTimeB", timeBModel).setRequired(true));
        formCriar.add(new TextField<>("novoPlacarB", placarBModel));
        formCriar.add(new Button("btnCriar"));
        add(formCriar);

        // ====================================================================
        // BLOCO 2: Botões / Tabs de Filtro de Status
        // ====================================================================
        add(new Link<Void>("filtroTodos") {
            private static final long serialVersionUID = 1L;
            @Override public void onClick() { filtroStatus = "TODOS"; }
        });
        add(new Link<Void>("filtroAndamento") {
            private static final long serialVersionUID = 1L;
            @Override public void onClick() { filtroStatus = "EM_ANDAMENTO"; }
        });
        add(new Link<Void>("filtroEncerrado") {
            private static final long serialVersionUID = 1L;
            @Override public void onClick() { filtroStatus = "ENCERRADO"; }
        });

        // Botão para sincronizar manualmente o cache Redis com alterações manuais no PostgreSQL
        add(new Link<Void>("btnSincronizarBanco") {
            private static final long serialVersionUID = 1L;
            @Override
            public void onClick() {
                try {
                    getRedisCache().limparTodos();
                    success("🔄 Cache limpo! A tela agora exibe exatamente os dados gravados no banco PostgreSQL.");
                } catch (Exception e) {
                    error("Erro ao sincronizar cache: " + e.getMessage());
                }
            }
        });

        // ====================================================================
        // BLOCO 3: Container de Partidas com Timer AJAX Silencioso
        // ====================================================================
        WebMarkupContainer containerPartidas = new WebMarkupContainer("containerPartidas");
        containerPartidas.setOutputMarkupId(true);
        // Atualiza a cada 3 segundos via AJAX sem recarregar a tela
        containerPartidas.add(new AjaxSelfUpdatingTimerBehavior(Duration.ofSeconds(3)));

        // LoadableDetachableModel garante que a lista seja reconsultada a cada ciclo AJAX
        IModel<List<PartidaViewModel>> partidasModel = new LoadableDetachableModel<>() {
            private static final long serialVersionUID = 1L;
            @Override
            protected List<PartidaViewModel> load() {
                return carregarPartidas();
            }
        };

        // ListView que renderiza cada linha de jogo
        ListView<PartidaViewModel> listView = new ListView<>("listaPartidas", partidasModel) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(ListItem<PartidaViewModel> item) {
                PartidaViewModel vm = item.getModelObject();
                boolean emAndamento = "EM_ANDAMENTO".equals(vm.getStatus());
                boolean encerrado = "ENCERRADO".equals(vm.getStatus());

                // Badge de status dinâmico (cores verde, amarelo ou cinza)
                Label lblStatus = new Label("statusBadge", vm.getStatus()) {
                    private static final long serialVersionUID = 1L;
                    @Override
                    protected void onComponentTag(ComponentTag tag) {
                        super.onComponentTag(tag);
                        String c = "badge-pill ";
                        if ("ENCERRADO".equals(vm.getStatus())) c += "badge-encerrado";
                        else c += "badge-andamento";
                        tag.put("class", c);
                    }
                };
                item.add(lblStatus);

                // Cabeçalho da partida exibindo Campeonato e dataHora formatada
                String infoCabecalho = "CAMPEONATO BRASILEIRO";
                if (vm.getDataHoraFormatada() != null && !vm.getDataHoraFormatada().isBlank()) {
                    infoCabecalho += " • " + vm.getDataHoraFormatada();
                }
                item.add(new Label("infoCabecalho", infoCabecalho));

                // Nomes dos times e placares
                item.add(new Label("timeA", vm.getTimeA()));
                item.add(new Label("timeB", vm.getTimeB()));
                item.add(new Label("placarA", String.valueOf(vm.getPlacarA())));
                item.add(new Label("placarB", String.valueOf(vm.getPlacarB())));
                item.add(new Label("origemPlacar", vm.isAoVivo() ? "⚡ AO VIVO (Redis)" : "💾 Banco (PostgreSQL)"));

                // ============================================================
                // Ações para Jogos EM_ANDAMENTO
                // ============================================================
                WebMarkupContainer controlesAndamento = new WebMarkupContainer("controlesAndamento");
                controlesAndamento.setVisible(emAndamento);

                // Botão: +1 Gol Mandante
                controlesAndamento.add(new Link<Void>("btnGolA") {
                    private static final long serialVersionUID = 1L;
                    @Override
                    public void onClick() {
                        try {
                            PartidaResponse atual = getPartidaService().buscarPorId(vm.getId());
                            int novoPlacarA = atual.getPlacarA() + 1;
                            getPartidaService().atualizarPlacar(atual.getId(), novoPlacarA, atual.getPlacarB(), "painel-web");
                            success("⚽ Gol do " + atual.getTimeA() + "! Placar: " + novoPlacarA + " x " + atual.getPlacarB());
                        } catch (Exception e) {
                            error("Erro ao adicionar gol: " + e.getMessage());
                        }
                    }
                });

                // Botão: ↩️ -1 Gol Mandante (Anular / Corrigir)
                controlesAndamento.add(new Link<Void>("btnAnularGolA") {
                    private static final long serialVersionUID = 1L;
                    @Override
                    public void onClick() {
                        try {
                            PartidaResponse atual = getPartidaService().buscarPorId(vm.getId());
                            if (atual.getPlacarA() <= 0) {
                                warn("O placar do " + atual.getTimeA() + " já está em zero. Não é possível anular.");
                                return;
                            }
                            int novoPlacarA = atual.getPlacarA() - 1;
                            getPartidaService().atualizarPlacar(atual.getId(), novoPlacarA, atual.getPlacarB(), "painel-web");
                            info("↩️ Placar corrigido para o " + atual.getTimeA() + "! Placar: " + novoPlacarA + " x " + atual.getPlacarB());
                        } catch (Exception e) {
                            error("Erro ao corrigir placar: " + e.getMessage());
                        }
                    }
                });

                // Botão: +1 Gol Visitante
                controlesAndamento.add(new Link<Void>("btnGolB") {
                    private static final long serialVersionUID = 1L;
                    @Override
                    public void onClick() {
                        try {
                            PartidaResponse atual = getPartidaService().buscarPorId(vm.getId());
                            int novoPlacarB = atual.getPlacarB() + 1;
                            getPartidaService().atualizarPlacar(atual.getId(), atual.getPlacarA(), novoPlacarB, "painel-web");
                            success("⚽ Gol do " + atual.getTimeB() + "! Placar: " + atual.getPlacarA() + " x " + novoPlacarB);
                        } catch (Exception e) {
                            error("Erro ao adicionar gol: " + e.getMessage());
                        }
                    }
                });

                // Botão: ↩️ -1 Gol Visitante (Anular / Corrigir)
                controlesAndamento.add(new Link<Void>("btnAnularGolB") {
                    private static final long serialVersionUID = 1L;
                    @Override
                    public void onClick() {
                        try {
                            PartidaResponse atual = getPartidaService().buscarPorId(vm.getId());
                            if (atual.getPlacarB() <= 0) {
                                warn("O placar do " + atual.getTimeB() + " já está em zero. Não é possível anular.");
                                return;
                            }
                            int novoPlacarB = atual.getPlacarB() - 1;
                            getPartidaService().atualizarPlacar(atual.getId(), atual.getPlacarA(), novoPlacarB, "painel-web");
                            info("↩️ Placar corrigido para o " + atual.getTimeB() + "! Placar: " + atual.getPlacarA() + " x " + novoPlacarB);
                        } catch (Exception e) {
                            error("Erro ao corrigir placar: " + e.getMessage());
                        }
                    }
                });

                // Botão: Encerrar Partida (bloqueia alterações posteriores conforme regra 2.1)
                controlesAndamento.add(new Link<Void>("btnEncerrar") {
                    private static final long serialVersionUID = 1L;
                    @Override
                    public void onClick() {
                        try {
                            getPartidaService().atualizarStatus(vm.getId(), StatusPartida.ENCERRADO, "painel-web");
                            info("🏁 Partida entre " + vm.getTimeA() + " e " + vm.getTimeB() + " encerrada.");
                        } catch (Exception e) {
                            error("Erro ao encerrar partida: " + e.getMessage());
                        }
                    }
                });
                item.add(controlesAndamento);

                // ============================================================
                // Ações para Jogos ENCERRADOS (Placar travado + opção de reabrir)
                // ============================================================
                WebMarkupContainer controlesEncerrado = new WebMarkupContainer("controlesEncerrado");
                controlesEncerrado.setVisible(encerrado);

                controlesEncerrado.add(new Link<Void>("btnReabrir") {
                    private static final long serialVersionUID = 1L;
                    @Override
                    public void onClick() {
                        try {
                            getPartidaService().atualizarStatus(vm.getId(), StatusPartida.EM_ANDAMENTO, "painel-web");
                            info("Partida reaberta para EM_ANDAMENTO.");
                        } catch (Exception e) {
                            error("Erro ao reabrir partida: " + e.getMessage());
                        }
                    }
                });
                item.add(controlesEncerrado);
            }
        };

        containerPartidas.add(listView);
        add(containerPartidas);
    }

    /**
     * Consulta as partidas no banco PostgreSQL e busca os placares em tempo real no Redis (Cache-Aside).
     */
    private List<PartidaViewModel> carregarPartidas() {
        List<PartidaViewModel> lista = new ArrayList<>();
        try {
            PartidaService ps = getPartidaService();
            RedisCacheService rc = getRedisCache();

            String statusParam = "TODOS".equalsIgnoreCase(filtroStatus) ? null : filtroStatus;
            List<PartidaResponse> partidas = ps.listar(statusParam, null, 0, 50);

            for (PartidaResponse p : partidas) {
                // Tenta buscar no Redis primeiro
                Optional<String> cacheJson = rc.buscarPlacar(p.getId());
                if (cacheJson.isPresent()) {
                    lista.add(PartidaViewModel.aoVivo(p, cacheJson.get()));
                } else {
                    // Fallback transparente para o banco de dados PostgreSQL
                    lista.add(PartidaViewModel.doBanco(p));
                }
            }
        } catch (Exception e) {
            // Em caso de inicialização ou falha temporária, retorna lista vazia
        }
        return lista;
    }

    /**
     * Obtém o serviço CDI de partidas dentro do contexto do Wicket.
     */
    private PartidaService getPartidaService() {
        return CDI.current().select(PartidaService.class).get();
    }

    /**
     * Obtém o serviço CDI do Redis dentro do contexto do Wicket.
     */
    private RedisCacheService getRedisCache() {
        return CDI.current().select(RedisCacheService.class).get();
    }
}