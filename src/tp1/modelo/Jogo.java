package tp1.modelo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Entidade do trabalho: um jogo do catálogo da Steam.
 *
 * A base cobre, sem nenhum campo inventado, todos os tipos exigidos no TP1:
 *
 *  - String de tamanho FIXO ....... faixaJogadores (8 bytes). É a faixa de
 *                                   proprietários publicada pelo SteamSpy,
 *                                   reduzida ao limite inferior: "10M+",
 *                                   "500K+", "<20K". Curta e regular por
 *                                   natureza, cabe num campo de largura fixa.
 *  - String de tamanho VARIÁVEL ... nome e desenvolvedora (writeUTF: 2 bytes
 *                                   de tamanho + conteúdo)
 *  - DATA ......................... dataLancamento, gravada como int com o
 *                                   "epoch day" — dias desde 01/01/1970. São
 *                                   4 bytes em vez dos ~10 de uma string, e a
 *                                   comparação vira uma subtração de inteiros.
 *  - LISTA com separador .......... generos, gravada como uma única string UTF
 *                                   com os itens separados por ';'
 *  - INTEIRO / FLOAT .............. avaliacoesPositivas e avaliacoesNegativas
 *                                   (int) / preco (float, em dólares)
 */
public class Jogo implements Registro {

    /** Separador escolhido pelo grupo para a lista de gêneros. */
    public static final char SEPARADOR_LISTA = ';';

    /** Bytes reservados para o campo de tamanho fixo. */
    public static final int TAM_FAIXA = 8;

    /** Formato usado na entrada/saída de datas pelo terminal e pelo CSV. */
    public static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ------------------------------------------------------------------ campos

    private int id;                    // chave primária (inteiro, vinda do cabeçalho)
    private String nome;               // string de tamanho variável
    private String desenvolvedora;     // string de tamanho variável
    private String faixaJogadores;     // string de tamanho FIXO (8 bytes)
    private LocalDate dataLancamento;  // data
    private String[] generos;          // lista de valores com separador ';'
    private float preco;               // float (dólares)
    private int avaliacoesPositivas;   // inteiro
    private int avaliacoesNegativas;   // inteiro

    // ------------------------------------------------------------ construtores

    /** Construtor vazio: obrigatório para o arquivo genérico instanciar o objeto. */
    public Jogo() {
        this.id = -1;
        this.nome = "";
        this.desenvolvedora = "";
        this.faixaJogadores = "";
        this.dataLancamento = LocalDate.now();
        this.generos = new String[0];
        this.preco = 0f;
        this.avaliacoesPositivas = 0;
        this.avaliacoesNegativas = 0;
    }

    public Jogo(int id, String nome, String desenvolvedora, String faixaJogadores,
                LocalDate dataLancamento, String[] generos, float preco,
                int avaliacoesPositivas, int avaliacoesNegativas) {
        this.id = id;
        this.nome = nome;
        this.desenvolvedora = desenvolvedora;
        this.faixaJogadores = faixaJogadores;
        this.dataLancamento = dataLancamento;
        this.generos = (generos == null) ? new String[0] : generos;
        this.preco = preco;
        this.avaliacoesPositivas = avaliacoesPositivas;
        this.avaliacoesNegativas = avaliacoesNegativas;
    }

    // ---------------------------------------------------------------- acesso

    @Override public int getId() { return id; }
    @Override public void setId(int id) { this.id = id; }

    public String getNome() { return nome; }
    public String getDesenvolvedora() { return desenvolvedora; }
    public String getFaixaJogadores() { return faixaJogadores; }
    public LocalDate getDataLancamento() { return dataLancamento; }
    public String[] getGeneros() { return generos; }
    public float getPreco() { return preco; }
    public int getAvaliacoesPositivas() { return avaliacoesPositivas; }
    public int getAvaliacoesNegativas() { return avaliacoesNegativas; }

    public void setNome(String v) { this.nome = v; }
    public void setDesenvolvedora(String v) { this.desenvolvedora = v; }
    public void setFaixaJogadores(String v) { this.faixaJogadores = v; }
    public void setDataLancamento(LocalDate v) { this.dataLancamento = v; }
    public void setGeneros(String[] v) { this.generos = (v == null) ? new String[0] : v; }
    public void setPreco(float v) { this.preco = v; }
    public void setAvaliacoesPositivas(int v) { this.avaliacoesPositivas = v; }
    public void setAvaliacoesNegativas(int v) { this.avaliacoesNegativas = v; }

    /** Devolve a lista de gêneros no formato "Action;RPG;Indie". */
    public String getGenerosConcatenados() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < generos.length; i++) {
            if (i > 0) sb.append(SEPARADOR_LISTA);
            sb.append(generos[i]);
        }
        return sb.toString();
    }

    /** Percentual de avaliações positivas; 0 quando o jogo não tem avaliação nenhuma. */
    public double getAprovacao() {
        int total = avaliacoesPositivas + avaliacoesNegativas;
        return total == 0 ? 0 : (avaliacoesPositivas * 100.0) / total;
    }

    // ----------------------------------------------------------- serialização

    /**
     * Converte o objeto no vetor de bytes que vai para o arquivo.
     * A ordem aqui é EXATAMENTE a ordem de leitura em fromByteArray();
     * qualquer divergência corrompe a leitura do arquivo inteiro.
     */
    @Override
    public byte[] toByteArray() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream saida = new DataOutputStream(buffer);

        saida.writeInt(id);                                   // 4 bytes  · inteiro
        saida.writeUTF(nome);                                 // variável · 2 + n
        saida.writeUTF(desenvolvedora);                       // variável · 2 + n
        saida.write(paraTamanhoFixo(faixaJogadores));         // 8 bytes  · tamanho FIXO
        saida.writeInt((int) dataLancamento.toEpochDay());    // 4 bytes  · data compactada
        saida.writeUTF(getGenerosConcatenados());             // variável · lista com ';'
        saida.writeFloat(preco);                              // 4 bytes  · float
        saida.writeInt(avaliacoesPositivas);                  // 4 bytes  · inteiro
        saida.writeInt(avaliacoesNegativas);                  // 4 bytes  · inteiro

        return buffer.toByteArray();
    }

    /** Reconstrói o objeto a partir dos bytes lidos do arquivo. */
    @Override
    public void fromByteArray(byte[] dados) throws IOException {
        DataInputStream entrada = new DataInputStream(new ByteArrayInputStream(dados));

        this.id = entrada.readInt();
        this.nome = entrada.readUTF();
        this.desenvolvedora = entrada.readUTF();

        byte[] fixo = new byte[TAM_FAIXA];
        entrada.readFully(fixo);                              // sempre lê os 8 bytes reservados
        this.faixaJogadores = new String(fixo, StandardCharsets.UTF_8).trim();

        this.dataLancamento = LocalDate.ofEpochDay(entrada.readInt());

        String lista = entrada.readUTF();
        this.generos = lista.isEmpty() ? new String[0] : lista.split(String.valueOf(SEPARADOR_LISTA));

        this.preco = entrada.readFloat();
        this.avaliacoesPositivas = entrada.readInt();
        this.avaliacoesNegativas = entrada.readInt();
    }

    /**
     * Normaliza uma string para exatamente TAM_FAIXA bytes: corta o que exceder
     * e completa com espaços o que faltar. É isso que garante o tamanho fixo.
     */
    private static byte[] paraTamanhoFixo(String valor) {
        byte[] destino = new byte[TAM_FAIXA];
        byte[] origem = (valor == null ? "" : valor).getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < TAM_FAIXA; i++) {
            destino[i] = (i < origem.length) ? origem[i] : (byte) ' ';
        }
        return destino;
    }

    // -------------------------------------------------------------- exibição

    /** Linha compacta usada nas listagens do menu. */
    public String paraLinha() {
        String n = nome.length() > 36 ? nome.substring(0, 33) + "..." : nome;
        return String.format("%-6d | %-36s | %10s | %7s | %8s | %9d | %3.0f%%",
                id, n,
                dataLancamento.format(FORMATO_DATA),
                faixaJogadores,
                preco == 0 ? "grátis" : String.format("$%.2f", preco),
                avaliacoesPositivas,
                getAprovacao());
    }

    @Override
    public String toString() {
        return "\n  ID .................... " + id +
               "\n  Nome .................. " + nome +
               "\n  Desenvolvedora ........ " + desenvolvedora +
               "\n  Faixa de jogadores .... " + faixaJogadores +
               "\n  Lançamento ............ " + dataLancamento.format(FORMATO_DATA) +
               "\n  Gêneros ............... " + getGenerosConcatenados() +
               "\n  Preço ................. " + (preco == 0 ? "grátis" : String.format("US$ %.2f", preco)) +
               "\n  Avaliações positivas .. " + avaliacoesPositivas +
               "\n  Avaliações negativas .. " + avaliacoesNegativas +
               "\n  Aprovação ............. " + String.format("%.1f%%", getAprovacao());
    }
}
