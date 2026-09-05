package tp1.arquivo;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tp1.modelo.Jogo;

/**
 * Responsável pela carga inicial: lê um arquivo CSV e transforma cada linha em
 * um registro do arquivo binário.
 *
 * Decisões de implementação:
 *
 *  1. O parser de CSV é próprio (não usa biblioteca externa) e trata campos
 *     entre aspas duplas, vírgulas dentro de aspas e aspas escapadas ("").
 *
 *  2. As colunas são localizadas pelo NOME do cabeçalho, não pela posição.
 *     Cada campo aceita vários "apelidos", o que permite carregar tanto o CSV
 *     de exemplo (em português) quanto o dataset original da Steam no Kaggle
 *     (em inglês) sem alterar código.
 *
 *  3. Os ids NÃO vêm do CSV: eles são gerados pelo cabeçalho do arquivo binário
 *     (int com o último id utilizado), como pede o enunciado.
 *
 *  4. Linhas malformadas não derrubam a carga: são contadas e reportadas ao
 *     final (robustez).
 */
public class CargaCSV {

    /** Resultado da carga, exibido no terminal ao final da importação. */
    public static class Resultado {
        public int importados;
        public int ignorados;
        public long milissegundos;
        public List<String> avisos = new ArrayList<>();
    }

    // Apelidos aceitos para cada campo (cabeçalho normalizado: minúsculo, sem acento/espaço).
    private static final String[] COL_NOME        = {"nome", "name", "titulo", "title"};
    private static final String[] COL_DEV         = {"desenvolvedora", "developer", "developers", "estudio"};
    private static final String[] COL_FAIXA       = {"faixa_jogadores", "faixajogadores", "owners", "proprietarios", "classificacao", "required_age"};
    private static final String[] COL_DATA        = {"data_lancamento", "datalancamento", "release_date", "releasedate", "data"};
    private static final String[] COL_GENEROS     = {"generos", "genres", "genre", "categorias", "categories"};
    private static final String[] COL_PRECO       = {"preco", "price"};
    private static final String[] COL_POSITIVAS   = {"avaliacoes_positivas", "avaliacoes", "positive", "positive_ratings", "recommendations"};
    private static final String[] COL_NEGATIVAS   = {"avaliacoes_negativas", "negative", "negative_ratings", "negativas"};

    /** Overload sem acompanhamento de progresso. */
    public static Resultado importar(String caminhoCSV, ArquivoSequencial<Jogo> arquivo, boolean limpar)
            throws Exception {
        return importar(caminhoCSV, arquivo, limpar, n -> { });
    }

    /**
     * Importa o CSV para o arquivo binário.
     *
     * @param caminhoCSV arquivo de origem
     * @param arquivo    arquivo binário de destino
     * @param limpar     se true, zera a base antes de importar
     * @param progresso  recebe a contagem parcial de vez em quando (bases grandes
     *                   levam segundos e o usuário precisa ver que algo acontece)
     */
    public static Resultado importar(String caminhoCSV, ArquivoSequencial<Jogo> arquivo, boolean limpar,
                                     java.util.function.IntConsumer progresso) throws Exception {

        Resultado r = new Resultado();
        long inicio = System.currentTimeMillis();

        if (limpar) arquivo.limpar();

        try (BufferedReader leitor = new BufferedReader(
                new InputStreamReader(new FileInputStream(caminhoCSV), StandardCharsets.UTF_8), 1 << 16);
             ArquivoSequencial<Jogo>.Lote lote = arquivo.abrirLote()) {

            String linhaCabecalho = leitor.readLine();
            if (linhaCabecalho == null) throw new IllegalArgumentException("CSV vazio.");

            // Remove BOM do UTF-8, comum em arquivos gerados pelo Excel.
            if (!linhaCabecalho.isEmpty() && linhaCabecalho.charAt(0) == '﻿') {
                linhaCabecalho = linhaCabecalho.substring(1);
            }

            String[] cabecalho = dividirLinhaCSV(linhaCabecalho);
            for (int i = 0; i < cabecalho.length; i++) cabecalho[i] = normalizar(cabecalho[i]);

            int iNome   = indiceDe(cabecalho, COL_NOME);
            int iDev    = indiceDe(cabecalho, COL_DEV);
            int iFaixa  = indiceDe(cabecalho, COL_FAIXA);
            int iData   = indiceDe(cabecalho, COL_DATA);
            int iGen    = indiceDe(cabecalho, COL_GENEROS);
            int iPreco  = indiceDe(cabecalho, COL_PRECO);
            int iPos    = indiceDe(cabecalho, COL_POSITIVAS);
            int iNeg    = indiceDe(cabecalho, COL_NEGATIVAS);

            if (iNome < 0) throw new IllegalArgumentException(
                    "O CSV precisa de uma coluna de nome (nome/name/titulo/title).");

            String linha;
            int numeroLinha = 1;
            while ((linha = leitor.readLine()) != null) {
                numeroLinha++;
                if (linha.isBlank()) continue;

                try {
                    String[] campos = dividirLinhaCSV(linha);

                    String nome = valor(campos, iNome);
                    if (nome.isEmpty()) { r.ignorados++; continue; }

                    Jogo jogo = new Jogo();
                    jogo.setNome(nome);
                    jogo.setDesenvolvedora(valor(campos, iDev));
                    jogo.setFaixaJogadores(valor(campos, iFaixa));
                    jogo.setDataLancamento(lerData(valor(campos, iData)));
                    jogo.setGeneros(lerLista(valor(campos, iGen)));
                    jogo.setPreco(lerFloat(valor(campos, iPreco)));
                    jogo.setAvaliacoesPositivas((int) lerFloat(valor(campos, iPos)));
                    jogo.setAvaliacoesNegativas((int) lerFloat(valor(campos, iNeg)));

                    lote.adicionar(jogo);   // id gerado a partir do cabeçalho do arquivo
                    r.importados++;
                    if (r.importados % 20_000 == 0) progresso.accept(r.importados);

                } catch (Exception e) {
                    r.ignorados++;
                    if (r.avisos.size() < 10) {
                        r.avisos.add("Linha " + numeroLinha + ": " + e.getMessage());
                    }
                }
            }
        }

        r.milissegundos = System.currentTimeMillis() - inicio;
        return r;
    }

    // ------------------------------------------------------------- utilitários

    /**
     * Divide uma linha de CSV respeitando aspas duplas.
     * Exemplo: Portal 2,"Ação, Puzzle",19.90  ->  ["Portal 2", "Ação, Puzzle", "19.90"]
     */
    public static String[] dividirLinhaCSV(String linha) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean dentroDeAspas = false;

        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);

            if (dentroDeAspas) {
                if (c == '"') {
                    // Aspas duplas seguidas representam uma aspa literal.
                    if (i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                        atual.append('"');
                        i++;
                    } else {
                        dentroDeAspas = false;
                    }
                } else {
                    atual.append(c);
                }
            } else {
                if (c == '"') dentroDeAspas = true;
                else if (c == ',') { campos.add(atual.toString().trim()); atual.setLength(0); }
                else atual.append(c);
            }
        }
        campos.add(atual.toString().trim());
        return campos.toArray(new String[0]);
    }

    /** Deixa o cabeçalho comparável: minúsculo, sem acentos e sem espaços. */
    private static String normalizar(String s) {
        String semAcento = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return semAcento.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
    }

    /** Procura, no cabeçalho, a primeira coluna que bata com algum apelido conhecido. */
    private static int indiceDe(String[] cabecalho, String[] apelidos) {
        for (String apelido : apelidos) {
            for (int i = 0; i < cabecalho.length; i++) {
                if (cabecalho[i].equals(apelido)) return i;
            }
        }
        return -1;
    }

    private static String valor(String[] campos, int indice) {
        if (indice < 0 || indice >= campos.length) return "";
        return campos[indice].trim();
    }

    /** Aceita dd/MM/yyyy, yyyy-MM-dd e "Aug 21, 2012" (formato do dataset da Steam). */
    private static LocalDate lerData(String texto) {
        if (texto == null || texto.isEmpty()) return LocalDate.of(1970, 1, 1);
        String t = texto.trim();
        DateTimeFormatter[] formatos = {
                Jogo.FORMATO_DATA,
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d MMM, yyyy", Locale.ENGLISH)
        };
        for (DateTimeFormatter f : formatos) {
            try { return LocalDate.parse(t, f); } catch (Exception ignorado) { }
        }
        // Último recurso: só o ano ("2012").
        if (t.matches("\\d{4}")) return LocalDate.of(Integer.parseInt(t), 1, 1);
        return LocalDate.of(1970, 1, 1);
    }

    /** Quebra a lista pelo separador ';' (ou ',' quando o campo veio entre aspas). */
    private static String[] lerLista(String texto) {
        if (texto == null || texto.isEmpty()) return new String[0];
        String[] partes = texto.split("[;,]");
        List<String> limpos = new ArrayList<>();
        for (String p : partes) {
            String v = p.trim();
            if (!v.isEmpty()) limpos.add(v);
        }
        return limpos.toArray(new String[0]);
    }

    /** Converte para float aceitando vírgula decimal e valores vazios/"Free". */
    private static float lerFloat(String texto) {
        if (texto == null || texto.isEmpty()) return 0f;
        String t = texto.replace("R$", "").replace("$", "").trim().replace(',', '.');
        if (t.equalsIgnoreCase("free") || t.equalsIgnoreCase("gratis")) return 0f;
        try { return Float.parseFloat(t); } catch (NumberFormatException e) { return 0f; }
    }

}
