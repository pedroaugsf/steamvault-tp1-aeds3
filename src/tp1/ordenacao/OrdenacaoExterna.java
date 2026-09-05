package tp1.ordenacao;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import tp1.arquivo.ArquivoSequencial;
import tp1.modelo.Jogo;

/**
 * ============================================================================
 * ORDENAÇÃO EXTERNA POR INTERCALAÇÃO BALANCEADA DE N CAMINHOS
 * ============================================================================
 *
 * O arquivo de dados normalmente é maior que a memória primária disponível.
 * Por isso a ordenação acontece em duas fases:
 *
 * FASE 1 — DISTRIBUIÇÃO
 *   O arquivo é lido sequencialmente e quebrado em BLOCOS ordenados ("runs"),
 *   distribuídos de forma circular entre N arquivos temporários. Registros com
 *   lápide '*' são simplesmente descartados aqui — é assim que a ordenação
 *   externa recupera o espaço perdido por updates e deletes.
 *
 *   Dois modos estão implementados:
 *
 *   (a) BLOCO FIXO — lê M registros, ordena em memória com Quicksort próprio
 *       e grava. Todo bloco tem exatamente M registros.
 *
 *   (b) BLOCO VARIÁVEL / SELEÇÃO POR SUBSTITUIÇÃO — mantém M registros em uma
 *       fila de prioridade e vai substituindo o menor à medida que grava. Os
 *       registros que "não cabem" no bloco corrente recebem o número da rodada
 *       seguinte. Resultado: blocos de tamanho VARIÁVEL, com 2·M registros em
 *       média — metade dos blocos, menos passadas de intercalação, menos I/O.
 *
 * FASE 2 — INTERCALAÇÃO
 *   A cada passada, N arquivos de entrada são intercalados em N arquivos de
 *   saída, sempre pegando o menor entre os N candidatos com a fila de
 *   prioridade (O(log N) por registro). O número de blocos cai por um fator N
 *   a cada passada; quando resta um único bloco, o arquivo está ordenado.
 *
 *   O fim de um bloco dentro de um arquivo temporário é detectado de forma
 *   implícita: como todo bloco é crescente, se o próximo registro for MENOR
 *   que o anterior, então um novo bloco começou.
 *
 * FASE 3 — REESCRITA
 *   O bloco final é convertido de volta para o formato do arquivo de dados
 *   (cabeçalho + lápide + tamanho + bytes) e substitui o arquivo original.
 *   As operações de CRUD seguintes passam a acontecer no arquivo ordenado.
 * ============================================================================
 */
public class OrdenacaoExterna {

    /** Métricas da execução — exibidas no terminal e usadas nos testes do vídeo. */
    public static class Resultado {
        public int registrosOrdenados;
        public int registrosDescartados;   // lápides eliminadas
        public int blocosIniciais;
        public int passadasIntercalacao;
        public int numCaminhos;
        public int registrosEmMemoria;
        public String modo;
        public String chave;
        public long bytesAntes;
        public long bytesDepois;
        public long milissegundos;

        public long bytesEconomizados() { return Math.max(0, bytesAntes - bytesDepois); }
    }

    // ====================================================================== API

    /**
     * @param caminhoDb              arquivo binário a ser ordenado
     * @param numCaminhos            N — quantos arquivos são intercalados por vez (>= 2)
     * @param maxRegistrosMemoria    M — quantos registros cabem na memória primária (>= 1)
     * @param chave                  campo usado como chave de ordenação
     * @param selecaoPorSubstituicao true = blocos de tamanho variável (otimização)
     */
    public static Resultado ordenar(String caminhoDb, int numCaminhos, int maxRegistrosMemoria,
                                    ChaveOrdenacao chave, boolean selecaoPorSubstituicao) throws Exception {

        if (numCaminhos < 2) throw new IllegalArgumentException("O número de caminhos deve ser >= 2.");
        if (maxRegistrosMemoria < 1) throw new IllegalArgumentException("O número de registros em memória deve ser >= 1.");

        Resultado r = new Resultado();
        r.numCaminhos = numCaminhos;
        r.registrosEmMemoria = maxRegistrosMemoria;
        r.chave = chave.getDescricao();
        r.modo = selecaoPorSubstituicao ? "Seleção por substituição (bloco variável)" : "Bloco fixo";
        long inicio = System.currentTimeMillis();

        File arquivoDados = new File(caminhoDb);
        r.bytesAntes = arquivoDados.length();

        Comparator<Jogo> cmp = chave.getComparador();

        // Pasta de trabalho: 2·N arquivos temporários (N de entrada + N de saída).
        File pastaTmp = new File(arquivoDados.getAbsoluteFile().getParentFile(), "tmp_ordenacao");
        limparPasta(pastaTmp);
        pastaTmp.mkdirs();

        File[] temporarios = new File[numCaminhos * 2];
        for (int i = 0; i < temporarios.length; i++) {
            temporarios[i] = new File(pastaTmp, "bloco_" + i + ".tmp");
        }

        try {
            // ------------------------------------------------- FASE 1
            int blocos = selecaoPorSubstituicao
                    ? distribuirComSelecaoPorSubstituicao(arquivoDados, temporarios, numCaminhos,
                                                          maxRegistrosMemoria, cmp, r)
                    : distribuirComBlocoFixo(arquivoDados, temporarios, numCaminhos,
                                             maxRegistrosMemoria, cmp, r);
            r.blocosIniciais = blocos;

            if (blocos == 0) {                       // base vazia: nada a fazer
                r.milissegundos = System.currentTimeMillis() - inicio;
                r.bytesDepois = arquivoDados.length();
                return r;
            }

            // ------------------------------------------------- FASE 2
            int origem = 0;                          // grupo de entrada: arquivos [0, N)
            int destino = numCaminhos;               // grupo de saída:   arquivos [N, 2N)
            int blocosRestantes = blocos;
            int arquivoFinal = origem;               // se só há 1 bloco, ele já está aqui

            while (blocosRestantes > 1) {
                r.passadasIntercalacao++;
                blocosRestantes = intercalarUmaPassada(temporarios, origem, destino, numCaminhos, cmp);
                arquivoFinal = destino;
                int troca = origem; origem = destino; destino = troca;   // alterna os grupos
            }

            // ------------------------------------------------- FASE 3
            reescreverArquivoDeDados(arquivoDados, temporarios[arquivoFinal]);
            r.bytesDepois = arquivoDados.length();

        } finally {
            limparPasta(pastaTmp);                   // sempre limpa os temporários
        }

        r.milissegundos = System.currentTimeMillis() - inicio;
        return r;
    }

    // ================================================== FASE 1 — DISTRIBUIÇÃO

    /**
     * Modo (a): blocos de tamanho fixo.
     * Lê M registros, ordena em memória com Quicksort próprio e grava o bloco
     * no próximo arquivo temporário (distribuição circular).
     */
    private static int distribuirComBlocoFixo(File arquivoDados, File[] temporarios, int n,
                                              int m, Comparator<Jogo> cmp, Resultado r) throws Exception {
        DataOutputStream[] saidas = abrirSaidas(temporarios, 0, n);
        int blocos = 0;
        int destino = 0;

        try (LeitorBaseDeDados leitor = new LeitorBaseDeDados(arquivoDados)) {
            List<Jogo> bloco = new ArrayList<>(m);
            Jogo jogo;
            while ((jogo = leitor.proximo()) != null) {
                bloco.add(jogo);
                r.registrosOrdenados++;

                if (bloco.size() == m) {
                    Ordenador.ordenar(bloco, cmp);           // ordenação em memória primária
                    for (Jogo j : bloco) gravar(saidas[destino], j);
                    bloco.clear();
                    destino = (destino + 1) % n;             // próximo caminho
                    blocos++;
                }
            }
            if (!bloco.isEmpty()) {                          // último bloco, possivelmente menor
                Ordenador.ordenar(bloco, cmp);
                for (Jogo j : bloco) gravar(saidas[destino], j);
                blocos++;
            }
            r.registrosDescartados = leitor.getDescartados();
        } finally {
            fechar(saidas);
        }
        return blocos;
    }

    /**
     * Modo (b): seleção por substituição — blocos de tamanho VARIÁVEL.
     *
     * A fila de prioridade guarda pares (registro, rodada). O menor par sai da
     * fila e é gravado; em seguida um novo registro entra. Se esse novo registro
     * for MENOR que o que acabou de ser gravado, ele não pode mais pertencer ao
     * bloco corrente (quebraria a ordem), então recebe rodada+1 e ficará para o
     * bloco seguinte. Quando o menor da fila pertence à rodada seguinte, o bloco
     * atual é encerrado e a gravação passa para o próximo arquivo temporário.
     *
     * Ganho: blocos com 2·M registros em média, ou seja, metade dos blocos e
     * tipicamente uma passada de intercalação a menos.
     */
    private static int distribuirComSelecaoPorSubstituicao(File arquivoDados, File[] temporarios, int n,
                                                           int m, Comparator<Jogo> cmp, Resultado r) throws Exception {
        DataOutputStream[] saidas = abrirSaidas(temporarios, 0, n);
        int blocos = 0;
        int destino = 0;

        // Ordena primeiro pela rodada, depois pela chave escolhida.
        Comparator<ItemSelecao> cmpItem = (a, b) -> {
            if (a.rodada != b.rodada) return Integer.compare(a.rodada, b.rodada);
            return cmp.compare(a.jogo, b.jogo);
        };

        try (LeitorBaseDeDados leitor = new LeitorBaseDeDados(arquivoDados)) {
            FilaDePrioridade<ItemSelecao> fila = new FilaDePrioridade<>(m, cmpItem);

            // Carga inicial: enche a memória com até M registros (rodada 0).
            Jogo jogo;
            while (fila.tamanho() < m && (jogo = leitor.proximo()) != null) {
                fila.inserir(new ItemSelecao(jogo, 0));
                r.registrosOrdenados++;
            }
            if (fila.vazia()) {
                r.registrosDescartados = leitor.getDescartados();
                fechar(saidas);
                return 0;
            }

            int rodadaAtual = 0;
            blocos = 1;

            while (!fila.vazia()) {
                ItemSelecao menor = fila.remover();

                // O menor já pertence à próxima rodada: fecha o bloco atual.
                if (menor.rodada != rodadaAtual) {
                    rodadaAtual = menor.rodada;
                    destino = (destino + 1) % n;
                    blocos++;
                }
                gravar(saidas[destino], menor.jogo);

                Jogo novo = leitor.proximo();
                if (novo != null) {
                    r.registrosOrdenados++;
                    // Se o novo é menor que o recém-gravado, ele fica para a rodada seguinte.
                    int rodada = (cmp.compare(novo, menor.jogo) < 0) ? rodadaAtual + 1 : rodadaAtual;
                    fila.inserir(new ItemSelecao(novo, rodada));
                }
            }
            r.registrosDescartados = leitor.getDescartados();
        } finally {
            fechar(saidas);
        }
        return blocos;
    }

    /** Par (registro, rodada) usado exclusivamente pela seleção por substituição. */
    private static class ItemSelecao {
        final Jogo jogo;
        final int rodada;
        ItemSelecao(Jogo jogo, int rodada) { this.jogo = jogo; this.rodada = rodada; }
    }

    // ================================================= FASE 2 — INTERCALAÇÃO

    /**
     * Executa UMA passada de intercalação: lê os N arquivos do grupo de origem
     * e grava blocos maiores, de forma circular, nos N arquivos do grupo destino.
     *
     * @return quantos blocos foram gerados nesta passada
     */
    private static int intercalarUmaPassada(File[] temporarios, int origem, int destino,
                                            int n, Comparator<Jogo> cmp) throws Exception {

        FonteDeBloco[] fontes = new FonteDeBloco[n];
        for (int i = 0; i < n; i++) fontes[i] = new FonteDeBloco(temporarios[origem + i], i);

        DataOutputStream[] saidas = abrirSaidas(temporarios, destino, n);

        // Menor registro primeiro; empate resolvido pelo índice da fonte (determinismo).
        Comparator<FonteDeBloco> cmpFonte = (a, b) -> {
            int c = cmp.compare(a.atual, b.atual);
            return (c != 0) ? c : Integer.compare(a.indice, b.indice);
        };

        int blocosGerados = 0;
        int d = 0;

        try {
            while (true) {
                // Cada volta deste laço intercala UM bloco de cada fonte disponível.
                FilaDePrioridade<FonteDeBloco> fila = new FilaDePrioridade<>(n, cmpFonte);
                for (FonteDeBloco f : fontes) {
                    if (!f.fim) fila.inserir(f);
                }
                if (fila.vazia()) break;             // todas as fontes acabaram

                while (!fila.vazia()) {
                    FonteDeBloco f = fila.remover();
                    Jogo gravado = f.atual;
                    gravar(saidas[d], gravado);
                    f.avancar();

                    // Se o próximo registro da fonte for menor, o bloco dela acabou:
                    // ela fica de fora até a próxima rodada de intercalação.
                    if (!f.fim && cmp.compare(f.atual, gravado) >= 0) {
                        fila.inserir(f);
                    }
                }
                blocosGerados++;
                d = (d + 1) % n;                     // próximo arquivo de saída
            }
        } finally {
            for (FonteDeBloco f : fontes) f.fechar();
            fechar(saidas);
        }
        return blocosGerados;
    }

    /** Leitor de um arquivo temporário, com um registro sempre "espiado" à frente. */
    private static class FonteDeBloco {
        final int indice;
        private DataInputStream entrada;
        Jogo atual;
        boolean fim;

        FonteDeBloco(File arquivo, int indice) throws Exception {
            this.indice = indice;
            if (!arquivo.exists() || arquivo.length() == 0) {
                this.fim = true;
                return;
            }
            this.entrada = new DataInputStream(new BufferedInputStream(new FileInputStream(arquivo), 64 * 1024));
            avancar();
        }

        void avancar() throws Exception {
            if (entrada == null) { fim = true; atual = null; return; }
            try {
                int tamanho = entrada.readInt();
                byte[] dados = new byte[tamanho];
                entrada.readFully(dados);
                Jogo j = new Jogo();
                j.fromByteArray(dados);
                atual = j;
            } catch (EOFException e) {
                fim = true;
                atual = null;
            }
        }

        void fechar() throws Exception {
            if (entrada != null) entrada.close();
        }
    }

    // =================================================== FASE 3 — REESCRITA

    /**
     * Converte o bloco final (formato temporário: tamanho + bytes) de volta ao
     * formato do arquivo de dados (cabeçalho + lápide + tamanho + bytes).
     *
     * A gravação é feita em um arquivo auxiliar e só então o original é
     * substituído — se algo falhar no meio do caminho, a base antiga continua
     * íntegra (robustez).
     */
    private static void reescreverArquivoDeDados(File arquivoDados, File blocoFinal) throws Exception {
        // Preserva o contador de ids do cabeçalho original.
        int ultimoId;
        try (RandomAccessFile raf = new RandomAccessFile(arquivoDados, "r")) {
            ultimoId = (raf.length() >= 4) ? raf.readInt() : 0;
        }

        File saidaTemp = new File(arquivoDados.getAbsolutePath() + ".ordenado");

        try (DataInputStream entrada = new DataInputStream(
                     new BufferedInputStream(new FileInputStream(blocoFinal), 64 * 1024));
             DataOutputStream saida = new DataOutputStream(
                     new BufferedOutputStream(new FileOutputStream(saidaTemp), 64 * 1024))) {

            saida.writeInt(ultimoId);                        // cabeçalho

            while (true) {
                int tamanho;
                try { tamanho = entrada.readInt(); }
                catch (EOFException e) { break; }

                byte[] dados = new byte[tamanho];
                entrada.readFully(dados);

                saida.writeByte(ArquivoSequencial.LAPIDE_VALIDO); // todos válidos: lápides sumiram
                saida.writeInt(tamanho);
                saida.write(dados);
            }
        }

        Files.move(saidaTemp.toPath(), arquivoDados.toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    // ============================================================= UTILITÁRIOS

    /**
     * Percorre o arquivo de dados devolvendo apenas os registros VÁLIDOS.
     * Os registros com lápide são contados e descartados — é aqui que a
     * ordenação externa "compacta" a base.
     */
    private static class LeitorBaseDeDados implements AutoCloseable {
        private final RandomAccessFile arquivo;
        private int descartados = 0;

        LeitorBaseDeDados(File f) throws Exception {
            arquivo = new RandomAccessFile(f, "r");
            if (arquivo.length() >= ArquivoSequencial.TAMANHO_CABECALHO) {
                arquivo.seek(ArquivoSequencial.TAMANHO_CABECALHO);
            }
        }

        Jogo proximo() throws Exception {
            while (arquivo.getFilePointer() < arquivo.length()) {
                byte lapide = arquivo.readByte();
                int tamanho = arquivo.readInt();
                if (lapide != ArquivoSequencial.LAPIDE_VALIDO) {
                    arquivo.skipBytes(tamanho);
                    descartados++;
                    continue;
                }
                byte[] dados = new byte[tamanho];
                arquivo.readFully(dados);
                Jogo j = new Jogo();
                j.fromByteArray(dados);
                return j;
            }
            return null;
        }

        int getDescartados() { return descartados; }

        @Override public void close() throws Exception { arquivo.close(); }
    }

    /** Grava um registro no formato dos arquivos temporários: tamanho + bytes. */
    private static void gravar(DataOutputStream saida, Jogo jogo) throws Exception {
        byte[] dados = jogo.toByteArray();
        saida.writeInt(dados.length);
        saida.write(dados);
    }

    /** Abre (truncando) N arquivos de saída a partir de um deslocamento do vetor. */
    private static DataOutputStream[] abrirSaidas(File[] temporarios, int deslocamento, int n) throws Exception {
        DataOutputStream[] saidas = new DataOutputStream[n];
        for (int i = 0; i < n; i++) {
            saidas[i] = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(temporarios[deslocamento + i], false), 64 * 1024));
        }
        return saidas;
    }

    private static void fechar(DataOutputStream[] saidas) throws Exception {
        for (DataOutputStream s : saidas) {
            if (s != null) { s.flush(); s.close(); }
        }
    }

    private static void limparPasta(File pasta) {
        if (!pasta.exists()) return;
        File[] arquivos = pasta.listFiles();
        if (arquivos != null) for (File f : arquivos) f.delete();
        pasta.delete();
    }
}
