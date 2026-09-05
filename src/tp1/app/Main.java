package tp1.app;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import tp1.arquivo.ArquivoSequencial;
import tp1.arquivo.CargaCSV;
import tp1.modelo.Jogo;
import tp1.ordenacao.ChaveOrdenacao;
import tp1.ordenacao.OrdenacaoExterna;
import tp1.util.Terminal;

/**
 * SteamVault — TP1 de AEDS III
 *
 * Aplicação de terminal que faz o CRUD sequencial em arquivo binário e a
 * ordenação externa por intercalação balanceada de N caminhos.
 *
 * Esta classe é apenas a camada de APRESENTAÇÃO: toda a lógica de persistência
 * está em tp1.arquivo e toda a lógica de ordenação está em tp1.ordenacao.
 */
public class Main {

    /** Caminho padrão do arquivo binário de dados. */
    private static final String CAMINHO_DB = "dados/jogos.db";
    /** Caminho padrão do CSV de carga. */
    private static final String CAMINHO_CSV = "dados/steam.csv";
    /** Quantos registros são mostrados por página na listagem. */
    private static final int TAMANHO_PAGINA = 15;

    /** Arquivo binário aberto durante toda a sessão; todas as opções operam sobre ele. */
    private static ArquivoSequencial<Jogo> arquivo;

    /**
     * Abre (ou cria) o arquivo binário, mostra o menu e garante que o arquivo
     * seja fechado mesmo se algo falhar no meio da execução.
     */
    public static void main(String[] args) {
        try {
            arquivo = new ArquivoSequencial<>(CAMINHO_DB, Jogo.class);
            apresentacao();
            lacoPrincipal();
        } catch (Exception e) {
            Terminal.erro("Falha inesperada: " + e.getMessage());
            e.printStackTrace(Terminal.SAIDA);
        } finally {
            try { if (arquivo != null) arquivo.close(); } catch (Exception ignorado) { }
        }
    }

    // ------------------------------------------------------------ interface

    /** Tela de abertura: identifica o sistema e a etapa do trabalho. */
    private static void apresentacao() {
        Terminal.imprimir("");
        Terminal.linhaDupla();
        Terminal.imprimir("   ____  _                   __     __          _ _   ");
        Terminal.imprimir("  / ___|| |_ ___  __ _ _ __ _\\ \\   / /_ _ _   _| | |_ ");
        Terminal.imprimir("  \\___ \\| __/ _ \\/ _` | '_ ` _ \\ \\ / / _` | | | | | __|");
        Terminal.imprimir("   ___) | ||  __/ (_| | | | | | \\ V / (_| | |_| | | |_ ");
        Terminal.imprimir("  |____/ \\__\\___|\\__,_|_| |_| |_|\\_/ \\__,_|\\__,_|_|\\__|");
        Terminal.imprimir("");
        Terminal.imprimir("  AEDS III - TP1 | CRUD em arquivo sequencial + Ordenação externa");
        Terminal.linhaDupla();
    }

    /**
     * Laço do menu: imprime as opções, lê a escolha e despacha para o método
     * correspondente, até o usuário digitar 0. A leitura da opção já rejeita
     * entrada inválida, então nenhum valor fora de 0..9 chega ao switch.
     */
    private static void lacoPrincipal() throws Exception {
        boolean executando = true;
        while (executando) {
            Terminal.titulo("menu principal");
            Terminal.imprimir("  BASE DE DADOS");
            Terminal.imprimir("   1 - Carregar base a partir de um arquivo CSV");
            Terminal.imprimir("   2 - Listar registros (paginado)");
            Terminal.imprimir("   3 - Buscar por ID");
            Terminal.imprimir("   4 - Buscar por nome");
            Terminal.imprimir("");
            Terminal.imprimir("  CRUD SEQUENCIAL");
            Terminal.imprimir("   5 - Incluir um novo jogo");
            Terminal.imprimir("   6 - Atualizar um jogo");
            Terminal.imprimir("   7 - Excluir um jogo");
            Terminal.imprimir("");
            Terminal.imprimir("  ARQUIVO");
            Terminal.imprimir("   8 - Estatísticas do arquivo binário");
            Terminal.imprimir("   9 - Ordenação externa (intercalação balanceada)");
            Terminal.imprimir("");
            Terminal.imprimir("   0 - Sair");
            Terminal.linha();

            int opcao = Terminal.lerInteiro("Opção", 0, 9);
            switch (opcao) {
                case 1 -> carregarCSV();
                case 2 -> listar();
                case 3 -> buscarPorId();
                case 4 -> buscarPorNome();
                case 5 -> incluir();
                case 6 -> atualizar();
                case 7 -> excluir();
                case 8 -> estatisticas();
                case 9 -> ordenacaoExterna();
                case 0 -> executando = false;
            }
        }
        Terminal.imprimir("\n  Encerrado. Arquivo salvo em: " + new File(CAMINHO_DB).getAbsolutePath() + "\n");
    }

    // ------------------------------------------------------- 1. carga do CSV

    /**
     * Opção 1 — carga da base: importa um CSV para o arquivo binário.
     * Se já houver registros, pergunta antes se a base atual deve ser apagada,
     * para não duplicar o catálogo inteiro sem querer.
     */
    private static void carregarCSV() throws Exception {
        Terminal.titulo("carga da base de dados");
        Terminal.imprimir("  Informe o caminho do CSV ou apenas ENTER para usar o arquivo padrão.");
        String caminho = Terminal.lerTextoOuManter("Arquivo CSV", CAMINHO_CSV);

        File f = new File(caminho);
        if (!f.exists()) {
            Terminal.erro("Arquivo não encontrado: " + f.getAbsolutePath());
            Terminal.pausar();
            return;
        }

        boolean limpar = true;
        if (arquivo.estatisticas().validos > 0) {
            Terminal.aviso("Já existem registros na base.");
            limpar = Terminal.lerSimNao("Apagar a base atual antes de importar?", true);
        }

        Terminal.imprimir("\n  Importando...");
        CargaCSV.Resultado r = CargaCSV.importar(caminho, arquivo, limpar,
                n -> Terminal.imprimir("          " + n + " registros..."));

        Terminal.linha();
        Terminal.sucesso(r.importados + " registros importados em " + r.milissegundos + " ms.");
        if (r.ignorados > 0) {
            Terminal.aviso(r.ignorados + " linha(s) ignorada(s) por estarem malformadas:");
            for (String a : r.avisos) Terminal.imprimir("          - " + a);
        }
        ArquivoSequencial.Estatisticas e = arquivo.estatisticas();
        Terminal.imprimir("  Tamanho do arquivo binário: " + Terminal.formatarBytes(e.tamanhoArquivo));
        Terminal.pausar();
    }

    // ---------------------------------------------------------- 2. listagem

    /**
     * Opção 2 — listagem paginada. Pede ao arquivo apenas os registros da
     * página corrente, para não carregar dezenas de milhares de objetos na
     * memória só para mostrar quinze linhas.
     */
    private static void listar() throws Exception {
        int pagina = 0;
        while (true) {
            List<Jogo> jogos = arquivo.listar(pagina * TAMANHO_PAGINA, TAMANHO_PAGINA);
            Terminal.titulo("registros (página " + (pagina + 1) + ")");

            if (jogos.isEmpty()) {
                if (pagina == 0) {
                    Terminal.aviso("A base está vazia. Use a opção 1 para carregar o CSV.");
                    Terminal.pausar();
                    return;
                }
                Terminal.aviso("Não há mais registros.");
                pagina--;
            } else {
                Terminal.cabecalhoTabela();
                for (Jogo j : jogos) Terminal.imprimir(j.paraLinha());
            }

            Terminal.linha();
            Terminal.imprimir("  [P] próxima página   [A] página anterior   [V] voltar ao menu");
            String cmd = Terminal.lerTexto("Comando").toLowerCase();
            if (cmd.startsWith("p")) pagina++;
            else if (cmd.startsWith("a")) pagina = Math.max(0, pagina - 1);
            else return;
        }
    }

    // ------------------------------------------------------- 3. busca por id

    /**
     * Opção 3 — leitura por id, como pede o enunciado: recebe o id, percorre o
     * arquivo binário e devolve os dados. O tempo é cronometrado e exibido
     * porque é ele que deixa visível o custo O(n) da busca sequencial.
     */
    private static void buscarPorId() throws Exception {
        Terminal.titulo("buscar por id");
        int id = Terminal.lerInteiro("ID do jogo", 1, Integer.MAX_VALUE);

        long inicio = System.nanoTime();
        Jogo jogo = arquivo.read(id);
        double ms = (System.nanoTime() - inicio) / 1_000_000.0;

        if (jogo == null) {
            Terminal.erro("Nenhum registro válido com o ID " + id + ".");
        } else {
            Terminal.imprimir(jogo.toString());
            Terminal.imprimir(String.format("%n  (busca sequencial concluída em %.2f ms)", ms));
        }
        Terminal.pausar();
    }

    // ----------------------------------------------------- 4. busca por nome

    /**
     * Busca sequencial por trecho do nome. Como não há índice (isso é o TP2),
     * é necessário varrer o arquivo inteiro — o custo é O(n) e fica evidente
     * no tempo exibido.
     */
    private static void buscarPorNome() throws Exception {
        Terminal.titulo("buscar por nome");
        String termo = Terminal.lerTextoObrigatorio("Trecho do nome").toLowerCase();

        List<Jogo> encontrados = new ArrayList<>();
        long inicio = System.nanoTime();
        arquivo.percorrer(j -> {
            if (j.getNome().toLowerCase().contains(termo)) encontrados.add(j);
        });
        double ms = (System.nanoTime() - inicio) / 1_000_000.0;

        Terminal.linha();
        if (encontrados.isEmpty()) {
            Terminal.erro("Nenhum jogo encontrado com \"" + termo + "\".");
        } else {
            Terminal.cabecalhoTabela();
            for (Jogo j : encontrados) Terminal.imprimir(j.paraLinha());
            Terminal.imprimir(String.format("%n  %d resultado(s) em %.2f ms.", encontrados.size(), ms));
        }
        Terminal.pausar();
    }

    // --------------------------------------------------------- 5. incluir

    /**
     * Opção 5 — inclusão. O id não é pedido ao usuário: quem o atribui é o
     * cabeçalho do arquivo, e o registro vai sempre para o fim.
     */
    private static void incluir() throws Exception {
        Terminal.titulo("incluir novo jogo");

        Jogo jogo = new Jogo();
        jogo.setNome(Terminal.lerTextoObrigatorio("Nome"));
        jogo.setDesenvolvedora(Terminal.lerTextoOuManter("Desenvolvedora", "Indefinida"));
        jogo.setFaixaJogadores(Terminal.lerTextoOuManter("Faixa de jogadores (até 8 caracteres)", "<20K"));
        jogo.setDataLancamento(Terminal.lerDataOuManter("Lançamento", LocalDate.now()));
        jogo.setGeneros(Terminal.lerListaOuManter("Gêneros", new String[]{"Indie"}));
        jogo.setPreco(Terminal.lerFloatOuManter("Preço (US$)", 0f));
        jogo.setAvaliacoesPositivas(Terminal.lerInteiroNaoNegativoOuManter("Avaliações positivas", 0));
        jogo.setAvaliacoesNegativas(Terminal.lerInteiroNaoNegativoOuManter("Avaliações negativas", 0));

        int id = arquivo.create(jogo);
        Terminal.linha();
        Terminal.sucesso("Registro criado com o ID " + id + " e gravado no fim do arquivo.");
        Terminal.pausar();
    }

    // -------------------------------------------------------- 6. atualizar

    /**
     * Opção 6 — atualização. Mede o tamanho do registro antes e depois da
     * edição para informar ao usuário qual dos dois casos do enunciado
     * aconteceu: regravação no mesmo lugar ou lápide seguida de acréscimo
     * no fim do arquivo.
     */
    private static void atualizar() throws Exception {
        Terminal.titulo("atualizar jogo");
        int id = Terminal.lerInteiro("ID do jogo", 1, Integer.MAX_VALUE);

        Jogo atual = arquivo.read(id);
        if (atual == null) {
            Terminal.erro("Nenhum registro válido com o ID " + id + ".");
            Terminal.pausar();
            return;
        }

        Terminal.imprimir(atual.toString());
        Terminal.linha();
        Terminal.imprimir("  Pressione ENTER para manter o valor atual de cada campo.");
        Terminal.linha();

        int tamanhoAntes = atual.toByteArray().length;

        Jogo novo = new Jogo(
                atual.getId(),
                Terminal.lerTextoOuManter("Nome", atual.getNome()),
                Terminal.lerTextoOuManter("Desenvolvedora", atual.getDesenvolvedora()),
                Terminal.lerTextoOuManter("Faixa de jogadores", atual.getFaixaJogadores()),
                Terminal.lerDataOuManter("Lançamento", atual.getDataLancamento()),
                Terminal.lerListaOuManter("Gêneros", atual.getGeneros()),
                Terminal.lerFloatOuManter("Preço (US$)", atual.getPreco()),
                Terminal.lerInteiroNaoNegativoOuManter("Avaliações positivas", atual.getAvaliacoesPositivas()),
                Terminal.lerInteiroNaoNegativoOuManter("Avaliações negativas", atual.getAvaliacoesNegativas())
        );

        int tamanhoDepois = novo.toByteArray().length;
        boolean ok = arquivo.update(novo);

        Terminal.linha();
        if (!ok) {
            Terminal.erro("Não foi possível atualizar o registro.");
        } else if (tamanhoAntes == tamanhoDepois) {
            Terminal.sucesso("Tamanho inalterado (" + tamanhoAntes + " bytes): "
                    + "registro regravado no MESMO lugar.");
        } else {
            Terminal.sucesso("Tamanho mudou de " + tamanhoAntes + " para " + tamanhoDepois + " bytes: "
                    + "o registro antigo recebeu lápide e a nova versão foi gravada no FIM do arquivo.");
            Terminal.aviso("Esse espaço morto será recuperado na próxima ordenação externa (opção 9).");
        }
        Terminal.pausar();
    }

    // ----------------------------------------------------------- 7. excluir

    /**
     * Opção 7 — exclusão lógica. Mostra o registro e pede confirmação antes de
     * marcar a lápide, já que a operação não tem desfazer.
     */
    private static void excluir() throws Exception {
        Terminal.titulo("excluir jogo");
        int id = Terminal.lerInteiro("ID do jogo", 1, Integer.MAX_VALUE);

        Jogo jogo = arquivo.read(id);
        if (jogo == null) {
            Terminal.erro("Nenhum registro válido com o ID " + id + ".");
            Terminal.pausar();
            return;
        }

        Terminal.imprimir(jogo.toString());
        Terminal.linha();
        if (!Terminal.lerSimNao("Confirma a exclusão deste registro?", false)) {
            Terminal.aviso("Operação cancelada.");
            Terminal.pausar();
            return;
        }

        arquivo.delete(id);
        Terminal.sucesso("Registro marcado com lápide ('*'). Ele deixa de aparecer nas buscas, "
                + "mas ainda ocupa espaço no arquivo.");
        Terminal.pausar();
    }

    // ------------------------------------------------------ 8. estatísticas

    /**
     * Opção 8 — estatísticas. Percorre o arquivo somando bytes úteis e bytes
     * presos em registros mortos. É o número que justifica rodar a ordenação
     * externa e o que se compara antes e depois dela.
     */
    private static void estatisticas() throws Exception {
        Terminal.titulo("estatísticas do arquivo binário");
        ArquivoSequencial.Estatisticas e = arquivo.estatisticas();

        Terminal.imprimir("  Arquivo ................. " + new File(CAMINHO_DB).getAbsolutePath());
        Terminal.imprimir("  Tamanho total ........... " + Terminal.formatarBytes(e.tamanhoArquivo));
        Terminal.imprimir("  Último ID do cabeçalho .. " + arquivo.getUltimoId());
        Terminal.linha();
        Terminal.imprimir("  Registros válidos ....... " + e.validos
                + "  (" + Terminal.formatarBytes(e.bytesUteis) + ")");
        Terminal.imprimir("  Registros com lápide .... " + e.excluidos
                + "  (" + Terminal.formatarBytes(e.bytesDesperdicados) + ")");
        Terminal.imprimir(String.format("  Espaço desperdiçado ..... %.2f%%", e.percentualDesperdicado()));

        if (e.validos > 0) {
            Terminal.imprimir("  Tamanho médio do registro " + (e.bytesUteis / e.validos) + " bytes");
        }
        if (e.excluidos > 0) {
            Terminal.linha();
            Terminal.aviso("Rode a ordenação externa (opção 9) para recuperar o espaço.");
        }
        Terminal.pausar();
    }

    // -------------------------------------------------- 9. ordenação externa

    /**
     * Opção 9 — ordenação externa. Pede os dois parâmetros exigidos pelo
     * enunciado (N caminhos e M registros em memória) e mais duas escolhas
     * implementadas como otimização: a chave e o modo de geração dos blocos.
     *
     * O arquivo precisa ser fechado antes: a última fase substitui o .db no
     * disco. O reabrir() no finally garante que o CRUD continue funcionando
     * no arquivo ordenado mesmo se a ordenação falhar.
     */
    private static void ordenacaoExterna() throws Exception {
        Terminal.titulo("ordenação externa");

        ArquivoSequencial.Estatisticas antes = arquivo.estatisticas();
        if (antes.validos == 0) {
            Terminal.aviso("A base está vazia. Nada a ordenar.");
            Terminal.pausar();
            return;
        }

        Terminal.imprimir("  Registros válidos na base: " + antes.validos
                + " | com lápide: " + antes.excluidos);
        Terminal.linha();

        // ---- parâmetros exigidos pelo enunciado
        int caminhos = Terminal.lerInteiroOuPadrao("Número de caminhos (N)", 4, 2, 64);
        int memoria  = Terminal.lerInteiroOuPadrao("Registros em memória primária (M)", 100, 1, 1_000_000);

        // ---- chave de ordenação (mais de uma chave = otimização da revisão por pares)
        Terminal.linha();
        Terminal.imprimir("  Chave de ordenação:");
        ChaveOrdenacao[] chaves = ChaveOrdenacao.values();
        for (int i = 0; i < chaves.length; i++) {
            Terminal.imprimir("   " + (i + 1) + " - " + chaves[i].getDescricao());
        }
        int escolha = Terminal.lerInteiroOuPadrao("Chave", 1, 1, chaves.length);
        ChaveOrdenacao chave = chaves[escolha - 1];

        // ---- modo de geração dos blocos
        Terminal.linha();
        boolean substituicao = Terminal.lerSimNao(
                "Usar seleção por substituição (blocos de tamanho variável)?", true);

        Terminal.linha();
        Terminal.imprimir("  Executando...");

        // O arquivo precisa ser fechado: a fase final SUBSTITUI o .db no disco.
        arquivo.close();
        OrdenacaoExterna.Resultado r;
        try {
            r = OrdenacaoExterna.ordenar(CAMINHO_DB, caminhos, memoria, chave, substituicao);
        } finally {
            arquivo.reabrir();   // reabre para que o CRUD continue no arquivo ordenado
        }

        // ---- relatório da execução
        Terminal.linha();
        Terminal.sucesso("Ordenação concluída em " + r.milissegundos + " ms.");
        Terminal.imprimir("");
        Terminal.imprimir("  Chave ................... " + r.chave);
        Terminal.imprimir("  Modo .................... " + r.modo);
        Terminal.imprimir("  Caminhos (N) ............ " + r.numCaminhos);
        Terminal.imprimir("  Registros em memória (M)  " + r.registrosEmMemoria);
        Terminal.linha();
        Terminal.imprimir("  Registros ordenados ..... " + r.registrosOrdenados);
        Terminal.imprimir("  Lápides eliminadas ...... " + r.registrosDescartados);
        Terminal.imprimir("  Blocos iniciais gerados . " + r.blocosIniciais
                + (r.blocosIniciais > 0
                    ? String.format("  (média de %.1f registros por bloco)",
                        r.registrosOrdenados / (double) r.blocosIniciais)
                    : ""));
        Terminal.imprimir("  Passadas de intercalação  " + r.passadasIntercalacao);
        Terminal.linha();
        Terminal.imprimir("  Arquivo antes ........... " + Terminal.formatarBytes(r.bytesAntes));
        Terminal.imprimir("  Arquivo depois .......... " + Terminal.formatarBytes(r.bytesDepois));
        Terminal.imprimir("  Espaço recuperado ....... " + Terminal.formatarBytes(r.bytesEconomizados()));
        Terminal.imprimir("");
        Terminal.imprimir("  As próximas operações de CRUD já acontecem no arquivo ordenado.");
        Terminal.pausar();
    }
}
