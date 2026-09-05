package tp1.arquivo;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import tp1.modelo.Registro;

/**
 * Arquivo binário sequencial genérico com suporte a CRUD.
 *
 * ----------------------------------------------------------------------------
 * LAYOUT FÍSICO DO ARQUIVO (exatamente o exigido pelo enunciado)
 * ----------------------------------------------------------------------------
 *
 *  CABEÇALHO (4 bytes)
 *  +--------------------------+
 *  | int ultimoIdUtilizado    |
 *  +--------------------------+
 *
 *  REGISTRO (repetido N vezes, tamanho variável)
 *  +--------+------------------+--------------------------+
 *  | lápide | int tamanho      | byte[] dados do objeto   |
 *  | 1 byte | 4 bytes          | 'tamanho' bytes          |
 *  +--------+------------------+--------------------------+
 *
 *  A lápide vale ' ' (registro válido) ou '*' (registro excluído logicamente).
 *  O campo "tamanho" é mantido mesmo em registros excluídos: é ele que permite
 *  pular o registro morto e continuar a varredura sequencial.
 * ----------------------------------------------------------------------------
 *
 * A classe é genérica (<T extends Registro>) para não acoplar a persistência
 * ao domínio: basta que a entidade saiba se converter de/para byte[].
 */
public class ArquivoSequencial<T extends Registro> {

    /** Marca de registro válido. */
    public static final byte LAPIDE_VALIDO = ' ';
    /** Marca de registro logicamente excluído. */
    public static final byte LAPIDE_EXCLUIDO = '*';
    /** Tamanho do cabeçalho: apenas o int com o último id utilizado. */
    public static final long TAMANHO_CABECALHO = 4L;

    private final String caminho;
    private final Constructor<T> construtor;   // usado para instanciar T na leitura
    private RandomAccessFile arquivo;

    /**
     * @param caminho    caminho do arquivo .db
     * @param classe     classe da entidade (precisa ter construtor público sem argumentos)
     */
    public ArquivoSequencial(String caminho, Class<T> classe) throws Exception {
        this.caminho = caminho;
        this.construtor = classe.getConstructor();

        File f = new File(caminho);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();

        this.arquivo = new RandomAccessFile(caminho, "rw");
        // Arquivo novo (ou vazio): grava o cabeçalho zerado.
        if (arquivo.length() < TAMANHO_CABECALHO) {
            arquivo.seek(0);
            arquivo.writeInt(0);
        }
    }

    public String getCaminho() { return caminho; }

    /** Fecha o arquivo. Sempre chamado antes da ordenação externa trocar o arquivo de lugar. */
    public void close() throws Exception {
        if (arquivo != null) arquivo.close();
    }

    /** Reabre o arquivo (usado depois que a ordenação externa substitui o .db). */
    public void reabrir() throws Exception {
        close();
        arquivo = new RandomAccessFile(caminho, "rw");
        if (arquivo.length() < TAMANHO_CABECALHO) {
            arquivo.seek(0);
            arquivo.writeInt(0);
        }
    }

    // ================================================================= CREATE

    /**
     * Insere um novo registro NO FIM do arquivo.
     *
     * Passos:
     *  1. lê o último id do cabeçalho e incrementa (garante unicidade);
     *  2. regrava o cabeçalho;
     *  3. serializa o objeto;
     *  4. posiciona no fim e grava lápide + tamanho + bytes.
     */
    public int create(T obj) throws Exception {
        arquivo.seek(0);
        int ultimoId = arquivo.readInt();
        ultimoId++;
        arquivo.seek(0);
        arquivo.writeInt(ultimoId);

        obj.setId(ultimoId);
        byte[] dados = obj.toByteArray();

        arquivo.seek(arquivo.length());          // append: escrita sempre no fim
        arquivo.writeByte(LAPIDE_VALIDO);
        arquivo.writeInt(dados.length);
        arquivo.write(dados);

        return ultimoId;
    }

    /**
     * Insere preservando o id do objeto (usado apenas pela carga do CSV, que
     * traz ids já definidos, e pela ordenação externa ao reescrever o arquivo).
     * O cabeçalho é atualizado para o maior id visto.
     */
    public void createComId(T obj) throws Exception {
        arquivo.seek(0);
        int ultimoId = arquivo.readInt();
        if (obj.getId() > ultimoId) {
            arquivo.seek(0);
            arquivo.writeInt(obj.getId());
        }

        byte[] dados = obj.toByteArray();
        arquivo.seek(arquivo.length());
        arquivo.writeByte(LAPIDE_VALIDO);
        arquivo.writeInt(dados.length);
        arquivo.write(dados);
    }

    // ============================================================ CARGA EM LOTE

    /**
     * Abre uma sessão de inserção em massa, usada só pela carga do CSV.
     *
     * O create() normal é correto mas caro para dezenas de milhares de linhas:
     * cada inserção faz dois posicionamentos para acertar o cabeçalho e uma
     * escrita sem buffer. Numa base de 80 mil jogos isso custa minutos.
     *
     * O lote resolve mantendo o contador de ids na memória e escrevendo por um
     * fluxo com buffer, em modo de acréscimo. O cabeçalho é gravado uma única
     * vez, no fechamento. O resultado no disco é byte a byte idêntico ao de
     * chamar create() em sequência.
     */
    public Lote abrirLote() throws Exception {
        return new Lote();
    }

    /** Sessão de inserção em massa. Use sempre com try-with-resources. */
    public class Lote implements AutoCloseable {

        private final DataOutputStream saida;
        private int ultimoId;

        private Lote() throws Exception {
            arquivo.seek(0);
            ultimoId = arquivo.readInt();
            arquivo.close();                     // o fluxo com buffer assume o arquivo
            saida = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(caminho, true), 1 << 16));
        }

        /** Grava um registro no fim do arquivo, atribuindo o próximo id. */
        public int adicionar(T obj) throws Exception {
            obj.setId(++ultimoId);
            byte[] dados = obj.toByteArray();
            saida.writeByte(LAPIDE_VALIDO);
            saida.writeInt(dados.length);
            saida.write(dados);
            return ultimoId;
        }

        /** Descarrega o buffer, regrava o cabeçalho e devolve o arquivo ao modo normal. */
        @Override
        public void close() throws Exception {
            saida.flush();
            saida.close();
            arquivo = new RandomAccessFile(caminho, "rw");
            arquivo.seek(0);
            arquivo.writeInt(ultimoId);
        }
    }

    // =================================================================== READ

    /**
     * Busca sequencial por id.
     *
     * Percorre o arquivo do início ao fim pulando registros com lápide '*'.
     * É O(n) — e é justamente essa limitação que motiva os índices do TP2.
     */
    public T read(int id) throws Exception {
        arquivo.seek(TAMANHO_CABECALHO);
        while (arquivo.getFilePointer() < arquivo.length()) {
            byte lapide = arquivo.readByte();
            int tamanho = arquivo.readInt();
            byte[] dados = new byte[tamanho];
            arquivo.readFully(dados);

            if (lapide == LAPIDE_VALIDO) {
                T obj = construtor.newInstance();
                obj.fromByteArray(dados);
                if (obj.getId() == id) return obj;
            }
        }
        return null; // não encontrado
    }

    /**
     * Devolve uma "página" de registros válidos (usada na listagem do menu),
     * evitando carregar a base inteira na memória.
     *
     * @param deslocamento quantos registros válidos pular
     * @param limite       quantos registros devolver
     */
    public List<T> listar(int deslocamento, int limite) throws Exception {
        List<T> resultado = new ArrayList<>();
        int ignorados = 0;

        arquivo.seek(TAMANHO_CABECALHO);
        while (arquivo.getFilePointer() < arquivo.length() && resultado.size() < limite) {
            byte lapide = arquivo.readByte();
            int tamanho = arquivo.readInt();

            if (lapide != LAPIDE_VALIDO) {
                arquivo.skipBytes(tamanho);       // registro morto: pula sem desserializar
                continue;
            }
            byte[] dados = new byte[tamanho];
            arquivo.readFully(dados);

            if (ignorados < deslocamento) { ignorados++; continue; }

            T obj = construtor.newInstance();
            obj.fromByteArray(dados);
            resultado.add(obj);
        }
        return resultado;
    }

    /** Varre o arquivo inteiro e entrega cada registro válido ao consumidor informado. */
    public void percorrer(java.util.function.Consumer<T> consumidor) throws Exception {
        arquivo.seek(TAMANHO_CABECALHO);
        while (arquivo.getFilePointer() < arquivo.length()) {
            byte lapide = arquivo.readByte();
            int tamanho = arquivo.readInt();
            if (lapide != LAPIDE_VALIDO) { arquivo.skipBytes(tamanho); continue; }
            byte[] dados = new byte[tamanho];
            arquivo.readFully(dados);
            T obj = construtor.newInstance();
            obj.fromByteArray(dados);
            consumidor.accept(obj);
        }
    }

    // ================================================================= UPDATE

    /**
     * Atualiza um registro existente. Trata os dois casos previstos no enunciado:
     *
     *  (a) o novo registro tem EXATAMENTE o mesmo tamanho do antigo
     *      -> regrava os bytes no próprio lugar (in place), sem desperdício;
     *
     *  (b) o tamanho mudou (cresceu ou diminuiu)
     *      -> marca o registro antigo com lápide '*' e grava a versão nova no
     *         FIM do arquivo, preservando o id original.
     *
     * @return true se o registro foi encontrado e atualizado
     */
    public boolean update(T novo) throws Exception {
        long posicao = buscarPosicao(novo.getId());
        if (posicao < 0) return false;

        arquivo.seek(posicao);
        arquivo.readByte();                      // pula a lápide
        int tamanhoAntigo = arquivo.readInt();

        byte[] dados = novo.toByteArray();

        if (dados.length == tamanhoAntigo) {
            // Caso (a): cabe no mesmo espaço.
            arquivo.write(dados);
        } else {
            // Caso (b): apaga logicamente e reinsere no fim.
            arquivo.seek(posicao);
            arquivo.writeByte(LAPIDE_EXCLUIDO);

            arquivo.seek(arquivo.length());
            arquivo.writeByte(LAPIDE_VALIDO);
            arquivo.writeInt(dados.length);
            arquivo.write(dados);
        }
        return true;
    }

    // ================================================================= DELETE

    /**
     * Exclusão LÓGICA: apenas troca a lápide para '*'.
     * O espaço só é efetivamente recuperado na ordenação externa.
     */
    public boolean delete(int id) throws Exception {
        long posicao = buscarPosicao(id);
        if (posicao < 0) return false;
        arquivo.seek(posicao);
        arquivo.writeByte(LAPIDE_EXCLUIDO);
        return true;
    }

    // ================================================================ APOIO

    /**
     * Devolve a posição (byte) onde começa o registro válido com o id informado,
     * ou -1 se não existir. É o coração do update e do delete.
     */
    private long buscarPosicao(int id) throws Exception {
        arquivo.seek(TAMANHO_CABECALHO);
        while (arquivo.getFilePointer() < arquivo.length()) {
            long inicio = arquivo.getFilePointer();
            byte lapide = arquivo.readByte();
            int tamanho = arquivo.readInt();

            if (lapide != LAPIDE_VALIDO) { arquivo.skipBytes(tamanho); continue; }

            byte[] dados = new byte[tamanho];
            arquivo.readFully(dados);
            T obj = construtor.newInstance();
            obj.fromByteArray(dados);
            if (obj.getId() == id) return inicio;
        }
        return -1;
    }

    /** Esvazia o arquivo e reinicia o contador de ids (usado antes de uma nova carga). */
    public void limpar() throws Exception {
        arquivo.setLength(0);
        arquivo.seek(0);
        arquivo.writeInt(0);
    }

    public int getUltimoId() throws Exception {
        arquivo.seek(0);
        return arquivo.readInt();
    }

    /** Estatísticas do arquivo — úteis para demonstrar o ganho da ordenação externa. */
    public Estatisticas estatisticas() throws Exception {
        Estatisticas e = new Estatisticas();
        e.tamanhoArquivo = arquivo.length();
        arquivo.seek(TAMANHO_CABECALHO);
        while (arquivo.getFilePointer() < arquivo.length()) {
            byte lapide = arquivo.readByte();
            int tamanho = arquivo.readInt();
            arquivo.skipBytes(tamanho);
            if (lapide == LAPIDE_VALIDO) {
                e.validos++;
                e.bytesUteis += tamanho + 5;
            } else {
                e.excluidos++;
                e.bytesDesperdicados += tamanho + 5;
            }
        }
        return e;
    }

    /** Pequeno agregado com os números do arquivo. */
    public static class Estatisticas {
        public long tamanhoArquivo;
        public int validos;
        public int excluidos;
        public long bytesUteis;
        public long bytesDesperdicados;

        /** Percentual do arquivo ocupado por registros mortos. */
        public double percentualDesperdicado() {
            long total = bytesUteis + bytesDesperdicados;
            return total == 0 ? 0 : (bytesDesperdicados * 100.0) / total;
        }
    }
}
