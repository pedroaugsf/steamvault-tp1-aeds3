package tp1.coleta;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import tp1.arquivo.ArquivoSequencial;
import tp1.modelo.Jogo;

/**
 * Ferramenta auxiliar: lê a base binária e gera o bloco de dados que o site
 * embute.
 *
 * São duas coisas bem diferentes:
 *
 *  - AGREGADOS calculados sobre a base INTEIRA (todos os jogos): distribuição
 *    por ano, por gênero, por faixa de preço e por aprovação. Ocupam poucos
 *    kilobytes e permitem que os gráficos do site descrevam o catálogo todo.
 *
 *  - Uma AMOSTRA dos jogos mais avaliados, que alimenta o explorador do site.
 *    Embutir 80 mil registros deixaria a página pesada sem necessidade; a
 *    amostra dá o que uma pessoa consegue de fato navegar.
 *
 * Os gêneros são substituídos por índices de um dicionário para encolher o
 * arquivo — a mesma ideia de compressão por dicionário que aparece no TP3.
 *
 * Uso:  java -cp bin tp1.coleta.PrepararSite [base.db] [quantos-na-amostra]
 */
public class PrepararSite {

    public static void main(String[] args) throws Exception {
        String caminhoDb = args.length > 0 ? args[0] : "dados/jogos.db";
        int amostra = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        String saida = "site/dados.js";

        ArquivoSequencial<Jogo> arquivo = new ArquivoSequencial<>(caminhoDb, Jogo.class);
        List<Jogo> todos = new ArrayList<>();
        arquivo.percorrer(todos::add);
        ArquivoSequencial.Estatisticas est = arquivo.estatisticas();
        arquivo.close();

        if (todos.isEmpty()) {
            System.out.println("Base vazia: rode a carga do CSV antes.");
            return;
        }
        System.out.println("Lidos " + todos.size() + " registros de " + caminhoDb);

        // ---------------------------------------------------- agregados globais
        Map<Integer, Integer> porAno = new TreeMap<>();
        Map<String, Integer> porGenero = new LinkedHashMap<>();
        int[] porPreco = new int[6];        // grátis, <5, 5-15, 15-30, 30-60, 60+
        int[] porAprovacao = new int[5];    // <40, 40-60, 60-80, 80-95, 95+
        long somaBytes = 0;
        int comAvaliacao = 0;
        long somaAvaliacoes = 0;

        for (Jogo j : todos) {
            porAno.merge(j.getDataLancamento().getYear(), 1, Integer::sum);
            for (String g : j.getGeneros()) porGenero.merge(g, 1, Integer::sum);

            float p = j.getPreco();
            int faixa = (p == 0) ? 0 : (p < 5) ? 1 : (p < 15) ? 2 : (p < 30) ? 3 : (p < 60) ? 4 : 5;
            porPreco[faixa]++;

            int total = j.getAvaliacoesPositivas() + j.getAvaliacoesNegativas();
            if (total > 0) {
                comAvaliacao++;
                somaAvaliacoes += total;
                double a = j.getAprovacao();
                int fa = (a < 40) ? 0 : (a < 60) ? 1 : (a < 80) ? 2 : (a < 95) ? 3 : 4;
                porAprovacao[fa]++;
            }
            somaBytes += j.toByteArray().length + 5;
        }

        // ------------------------------------------------------------- amostra
        List<Jogo> maisAvaliados = new ArrayList<>(todos);
        maisAvaliados.sort(Comparator.comparingInt(
                (Jogo j) -> j.getAvaliacoesPositivas() + j.getAvaliacoesNegativas()).reversed());
        if (maisAvaliados.size() > amostra) maisAvaliados = maisAvaliados.subList(0, amostra);

        // Dicionário de gêneros: o site guarda índices, não as strings repetidas.
        List<String> dicionario = new ArrayList<>(porGenero.keySet());

        // ------------------------------------------------------------- escrita
        File f = new File(saida);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();

        try (BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8), 1 << 16)) {

            w.write("/* Gerado por tp1.coleta.PrepararSite a partir da base binária. Não editar à mão. */\n");
            w.write("const DADOS = {\n");
            w.write("  total: " + todos.size() + ",\n");
            w.write("  bytes: " + est.tamanhoArquivo + ",\n");
            w.write("  bytesMedios: " + (somaBytes / todos.size()) + ",\n");
            w.write("  comAvaliacao: " + comAvaliacao + ",\n");
            w.write("  totalAvaliacoes: " + somaAvaliacoes + ",\n");
            w.write("  geradoEm: \"" + LocalDate.now() + "\",\n");

            w.write("  anos: [");
            boolean primeiro = true;
            for (Map.Entry<Integer, Integer> e : porAno.entrySet()) {
                if (e.getKey() < 1995 || e.getKey() > LocalDate.now().getYear() + 1) continue;
                if (!primeiro) w.write(",");
                w.write("[" + e.getKey() + "," + e.getValue() + "]");
                primeiro = false;
            }
            w.write("],\n");

            List<Map.Entry<String, Integer>> generos = new ArrayList<>(porGenero.entrySet());
            generos.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
            w.write("  generos: [");
            for (int i = 0; i < generos.size(); i++) {
                if (i > 0) w.write(",");
                w.write("[" + texto(generos.get(i).getKey()) + "," + generos.get(i).getValue() + "]");
            }
            w.write("],\n");

            w.write("  precos: " + vetor(porPreco) + ",\n");
            w.write("  aprovacao: " + vetor(porAprovacao) + ",\n");

            w.write("  dic: [");
            for (int i = 0; i < dicionario.size(); i++) {
                if (i > 0) w.write(",");
                w.write(texto(dicionario.get(i)));
            }
            w.write("],\n");

            // jogos: [nome, desenvolvedora, faixa, epochDay, [indices de gênero], preço, pos, neg]
            w.write("  jogos: [\n");
            for (int i = 0; i < maisAvaliados.size(); i++) {
                Jogo j = maisAvaliados.get(i);
                StringBuilder idx = new StringBuilder("[");
                String[] gs = j.getGeneros();
                for (int k = 0; k < gs.length; k++) {
                    int pos = dicionario.indexOf(gs[k]);
                    if (pos < 0) continue;
                    if (idx.length() > 1) idx.append(",");
                    idx.append(pos);
                }
                idx.append("]");

                w.write("[" + texto(j.getNome()) + "," + texto(j.getDesenvolvedora()) + ","
                        + texto(j.getFaixaJogadores()) + "," + j.getDataLancamento().toEpochDay() + ","
                        + idx + "," + String.format(Locale.US, "%.2f", j.getPreco()) + ","
                        + j.getAvaliacoesPositivas() + "," + j.getAvaliacoesNegativas() + "]");
                if (i < maisAvaliados.size() - 1) w.write(",");
                w.write("\n");
            }
            w.write("  ]\n};\n");
        }

        System.out.println("Gerado " + saida + " · "
                + String.format(Locale.US, "%.1f", f.length() / 1024.0) + " KB · "
                + maisAvaliados.size() + " jogos na amostra · "
                + dicionario.size() + " gêneros");
    }

    /** Serializa uma string como literal JavaScript, escapando o necessário. */
    private static String texto(String v) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : (v == null ? "" : v).toCharArray()) {
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n', '\r', '\t' -> sb.append(' ');
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) sb.append(' ');
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    private static String vetor(int[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(v[i]);
        }
        return sb.append("]").toString();
    }
}
