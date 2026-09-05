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
 * Bateria de testes automatizados.
 *
 * Executa, sem interação do usuário, o roteiro completo exigido pelo TP1 e
 * verifica os resultados: carga do CSV, os quatro casos de CRUD (incluindo os
 * dois cenários de update previstos no enunciado) e a ordenação externa em
 * todas as chaves e nos dois modos de geração de blocos.
 *
 * Uso:  java -cp bin tp1.app.Testes
 *
 * Trabalha em uma base separada (dados/teste.db) para não interferir na base
 * usada pelo menu principal.
 */
public class Testes {

    /** Base própria dos testes, separada da base do menu para não interferir nela. */
    private static final String DB_TESTE = "dados/teste.db";
    /** CSV de origem: o mesmo que o menu importa. */
    private static final String CSV = "dados/steam.csv";

    /** Contadores do resumo final; o programa sai com código 1 se houver falha. */
    private static int aprovados = 0;
    private static int reprovados = 0;

    /**
     * Roteiro completo do TP1, na ordem em que ele é cobrado: carga, leitura,
     * os dois casos de update, exclusão, inclusão, ordenação externa em todas
     * as chaves e modos, e o CRUD depois da ordenação.
     */
    public static void main(String[] args) throws Exception {
        Terminal.linhaDupla();
        Terminal.imprimir("  BATERIA DE TESTES AUTOMATIZADOS - TP1 AEDS III");
        Terminal.linhaDupla();

        new File(DB_TESTE).delete();
        ArquivoSequencial<Jogo> arq = new ArquivoSequencial<>(DB_TESTE, Jogo.class);

        // ------------------------------------------------------------ carga
        Terminal.titulo("1. carga do csv");
        CargaCSV.Resultado carga = CargaCSV.importar(CSV, arq, true);
        Terminal.imprimir("  Importados: " + carga.importados + " | ignorados: " + carga.ignorados
                + " | tempo: " + carga.milissegundos + " ms");
        verificar("A carga importou registros", carga.importados > 0);
        verificar("Nenhuma linha foi descartada", carga.ignorados == 0);

        ArquivoSequencial.Estatisticas est = arq.estatisticas();
        verificar("Todos os registros estão válidos após a carga",
                est.validos == carga.importados && est.excluidos == 0);
        verificar("O cabeçalho guarda o último id utilizado",
                arq.getUltimoId() == carga.importados);

        // ------------------------------------------------------------- read
        Terminal.titulo("2. leitura por id");
        Jogo primeiro = arq.read(1);
        Jogo ultimo = arq.read(carga.importados);
        verificar("Leitura do primeiro registro (id 1)", primeiro != null && primeiro.getId() == 1);
        verificar("Leitura do último registro", ultimo != null && ultimo.getId() == carga.importados);
        verificar("Id inexistente devolve null", arq.read(999_999) == null);
        verificar("Campo de tamanho fixo cabe nos 8 bytes",
                primeiro.getFaixaJogadores().length() <= Jogo.TAM_FAIXA);
        verificar("Lista de gêneros foi lida com o separador ';'",
                primeiro.getGeneros().length > 0);
        Terminal.imprimir("  Registro lido: " + primeiro.getNome()
                + " | gêneros: " + primeiro.getGenerosConcatenados());

        // -------------------------------------------- update do MESMO tamanho
        Terminal.titulo("3. update que mantém o tamanho (regravação no lugar)");
        Jogo alvo = arq.read(2);
        long tamanhoAntes = new File(DB_TESTE).length();
        String nomeOriginal = alvo.getNome();
        // Troca cada caractere do nome por 'X': mesmo comprimento, mesmos bytes.
        alvo.setNome("X".repeat(nomeOriginal.length()));
        arq.update(alvo);
        long tamanhoDepois = new File(DB_TESTE).length();

        verificar("O arquivo NÃO cresceu", tamanhoAntes == tamanhoDepois);
        verificar("Nenhuma lápide foi criada", arq.estatisticas().excluidos == 0);
        verificar("O novo valor foi persistido",
                arq.read(2).getNome().equals("X".repeat(nomeOriginal.length())));

        // Volta ao valor original para os testes seguintes.
        alvo.setNome(nomeOriginal);
        arq.update(alvo);

        // ------------------------------------------ update com tamanho MAIOR
        Terminal.titulo("4. update que altera o tamanho (lápide + append)");
        Jogo alvo2 = arq.read(3);
        String nomeExpandido = alvo2.getNome() + " - Edição Definitiva Completa";
        alvo2.setNome(nomeExpandido);
        long antes2 = new File(DB_TESTE).length();
        arq.update(alvo2);
        long depois2 = new File(DB_TESTE).length();

        verificar("O arquivo cresceu (registro reescrito no fim)", depois2 > antes2);
        verificar("O registro antigo virou lápide", arq.estatisticas().excluidos == 1);
        verificar("O id foi preservado", arq.read(3).getId() == 3);
        verificar("O novo conteúdo é lido corretamente",
                arq.read(3).getNome().equals(nomeExpandido));
        verificar("A contagem de registros válidos não mudou",
                arq.estatisticas().validos == carga.importados);

        // ----------------------------------------------------------- delete
        Terminal.titulo("5. exclusão lógica (lápide)");
        arq.delete(5);
        arq.delete(7);
        arq.delete(11);
        ArquivoSequencial.Estatisticas depoisDelete = arq.estatisticas();

        verificar("Registro excluído não é mais encontrado", arq.read(5) == null);
        verificar("Três lápides adicionais foram gravadas", depoisDelete.excluidos == 4);
        verificar("Registros válidos caíram para " + (carga.importados - 3),
                depoisDelete.validos == carga.importados - 3);
        verificar("Excluir um id inexistente devolve false", !arq.delete(999_999));
        Terminal.imprimir(String.format("  Espaço desperdiçado: %.2f%%", depoisDelete.percentualDesperdicado()));

        // ------------------------------------------------------------ create
        Terminal.titulo("6. inclusão de novo registro");
        Jogo novo = new Jogo(-1, "Jogo de Teste AEDS", "DTI Digital", "<20K",
                LocalDate.of(2026, 9, 5), new String[]{"Educacional", "Indie"}, 0f, 1, 0);
        int novoId = arq.create(novo);
        verificar("O id foi gerado pelo cabeçalho", novoId == carga.importados + 1);
        verificar("O registro criado é recuperável", arq.read(novoId) != null);
        verificar("A data foi persistida corretamente",
                arq.read(novoId).getDataLancamento().equals(LocalDate.of(2026, 9, 5)));

        int validosAntesDaOrdenacao = arq.estatisticas().validos;
        int lapidesAntesDaOrdenacao = arq.estatisticas().excluidos;
        long bytesAntesDaOrdenacao = new File(DB_TESTE).length();

        // ------------------------------------------------- ordenação externa
        Terminal.titulo("7. ordenação externa - todas as chaves e modos");
        arq.close();

        // N fixo em 4 caminhos; M dimensionado para render cerca de 40 blocos,
        // que é onde a intercalação fica interessante qualquer que seja o
        // tamanho da base (algumas centenas de registros ou as dezenas de milhares
        // da base completa da Steam).
        final int N_CAMINHOS = 4;
        final int M_MEMORIA = Math.max(20, validosAntesDaOrdenacao / 40);
        Terminal.imprimir("  Parâmetros: N = " + N_CAMINHOS + " caminhos, M = "
                + M_MEMORIA + " registros em memória, sobre "
                + validosAntesDaOrdenacao + " registros válidos.\n");

        boolean tudoOk = true;
        for (ChaveOrdenacao chave : ChaveOrdenacao.values()) {
            for (boolean substituicao : new boolean[]{false, true}) {
                // A base é reconstruída antes de cada rodada. Sem isso, a segunda
                // rodada receberia um arquivo já ordenado pela primeira e o número
                // de blocos gerados não seria comparável entre os dois modos.
                reconstruirBase();

                OrdenacaoExterna.Resultado r = OrdenacaoExterna.ordenar(
                        DB_TESTE, N_CAMINHOS, M_MEMORIA, chave, substituicao);

                arq.reabrir();
                List<Jogo> todos = new ArrayList<>();
                arq.percorrer(todos::add);
                arq.close();

                boolean ordenado = estaOrdenado(todos, chave);
                boolean semLapides = arq2Estatisticas().excluidos == 0;
                boolean contagemOk = todos.size() == validosAntesDaOrdenacao;

                String rotulo = String.format("%-45s %s",
                        chave.name() + (substituicao ? " / substituição" : " / bloco fixo"),
                        String.format("%d blocos, %d passadas, %d ms",
                                r.blocosIniciais, r.passadasIntercalacao, r.milissegundos));

                boolean ok = ordenado && semLapides && contagemOk;
                tudoOk &= ok;
                verificar(rotulo, ok);
                if (!ok) {
                    Terminal.erro("    ordenado=" + ordenado + " semLapides=" + semLapides
                            + " contagem=" + todos.size() + "/" + validosAntesDaOrdenacao);
                }
            }
        }

        arq.reabrir();
        long bytesDepois = new File(DB_TESTE).length();
        verificar("A ordenação eliminou as " + lapidesAntesDaOrdenacao + " lápides e reduziu o arquivo",
                bytesDepois < bytesAntesDaOrdenacao);
        Terminal.imprimir("  Antes: " + Terminal.formatarBytes(bytesAntesDaOrdenacao)
                + " -> depois: " + Terminal.formatarBytes(bytesDepois));

        // --------------------------------------- CRUD após a ordenação
        Terminal.titulo("8. crud continua funcionando no arquivo ordenado");
        Jogo aposOrdenacao = arq.read(1);
        verificar("Leitura por id funciona após a ordenação", aposOrdenacao != null);
        verificar("O cabeçalho preservou o contador de ids",
                arq.getUltimoId() == carga.importados + 1);

        int idNovo = arq.create(new Jogo(-1, "Pós-ordenação", "Teste", "<20K",
                LocalDate.now(), new String[]{"Teste"}, 1.99f, 0, 0));
        verificar("Inserção após a ordenação recebe um id novo", idNovo == carga.importados + 2);
        verificar("O registro inserido é recuperável", arq.read(idNovo) != null);
        arq.delete(idNovo);
        verificar("Exclusão após a ordenação funciona", arq.read(idNovo) == null);

        arq.close();
        new File(DB_TESTE).delete();

        // ------------------------------------------------------------ resumo
        Terminal.titulo("resumo");
        Terminal.imprimir("  Aprovados: " + aprovados);
        Terminal.imprimir("  Reprovados: " + reprovados);
        Terminal.linhaDupla();
        if (reprovados == 0 && tudoOk) {
            Terminal.imprimir("  TODOS OS TESTES PASSARAM.");
        } else {
            Terminal.imprimir("  EXISTEM FALHAS. Verifique a saída acima.");
        }
        Terminal.linhaDupla();

        if (reprovados > 0) System.exit(1);
    }

    /**
     * Recria a base de teste no mesmo estado usado nas comparações: 131 registros
     * importados, um update que gerou lápide, três exclusões e uma inclusão.
     * Garante que todas as rodadas de ordenação partam exatamente do mesmo arquivo.
     */
    private static void reconstruirBase() throws Exception {
        ArquivoSequencial<Jogo> tmp = new ArquivoSequencial<>(DB_TESTE, Jogo.class);
        CargaCSV.importar(CSV, tmp, true);

        Jogo j = tmp.read(3);
        j.setNome(j.getNome() + " - Edição Definitiva Completa");
        tmp.update(j);                       // gera 1 lápide

        tmp.delete(5);
        tmp.delete(7);
        tmp.delete(11);                      // gera 3 lápides

        tmp.create(new Jogo(-1, "Jogo de Teste AEDS", "DTI Digital", "<20K",
                LocalDate.of(2026, 9, 5), new String[]{"Educacional", "Indie"}, 0f, 1, 0));
        tmp.close();
    }

    /** Reabre o arquivo só para conferir as estatísticas entre as rodadas de ordenação. */
    private static ArquivoSequencial.Estatisticas arq2Estatisticas() throws Exception {
        ArquivoSequencial<Jogo> tmp = new ArquivoSequencial<>(DB_TESTE, Jogo.class);
        ArquivoSequencial.Estatisticas e = tmp.estatisticas();
        tmp.close();
        return e;
    }

    /** Confere, par a par, se a lista está em ordem não decrescente segundo a chave. */
    private static boolean estaOrdenado(List<Jogo> lista, ChaveOrdenacao chave) {
        for (int i = 1; i < lista.size(); i++) {
            if (chave.getComparador().compare(lista.get(i - 1), lista.get(i)) > 0) return false;
        }
        return true;
    }

    /** Registra o resultado de uma verificação e alimenta o resumo final. */
    private static void verificar(String descricao, boolean condicao) {
        if (condicao) {
            aprovados++;
            Terminal.imprimir("  [ OK ] " + descricao);
        } else {
            reprovados++;
            Terminal.imprimir("  [FALHA] " + descricao);
        }
    }
}
