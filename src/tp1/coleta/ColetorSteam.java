package tp1.coleta;

import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ============================================================================
 * COLETOR DA BASE DE DADOS
 * ============================================================================
 *
 * Ferramenta auxiliar: monta o CSV do catálogo da Steam a partir de fontes
 * públicas. Não faz parte do sistema avaliado — ela só produz o arquivo que a
 * opção 1 do menu importa. Usa apenas a biblioteca padrão do Java.
 *
 * Nenhuma fonte pública entrega todos os campos de uma vez, então a coleta é
 * feita em três fases e os resultados são cruzados pelo appid:
 *
 *   FASE A · SteamSpy (request=all)
 *            87 páginas de 1000 registros. Traz desenvolvedora, preço,
 *            avaliações positivas e negativas e a faixa de proprietários.
 *
 *   FASE B · SteamSpy (request=genre)
 *            Uma chamada por gênero. Traz a lista de gêneros de cada jogo —
 *            o campo "lista de valores com separador" exigido pelo trabalho.
 *
 *   FASE C · Busca da loja Steam (search/results)
 *            Páginas de 100 resultados, filtradas em "Jogos". Traz o nome
 *            oficial e, principalmente, a DATA DE LANÇAMENTO, que nenhuma das
 *            outras fontes fornece em massa.
 *
 * Ao final ficam no CSV apenas os jogos presentes nas três fases, ou seja,
 * registros completos. Jogos sem data (ainda não lançados) são descartados.
 *
 * Uso:  java -cp bin tp1.coleta.ColetorSteam [arquivo-de-saida] [limite-de-jogos]
 * ============================================================================
 */
public class ColetorSteam {

    private static final String UA = "Mozilla/5.0 (compatible; TP1-AEDS-III/1.0)";
    private static final int POR_PAGINA_BUSCA = 100;
    private static final int THREADS_BUSCA = 4;       // paralelismo moderado, para não abusar da API

    /** Gêneros catalogados pela Steam; a fase B percorre um a um. */
    private static final String[] GENEROS = {
        "Action", "Adventure", "Casual", "Indie", "Massively Multiplayer", "Racing",
        "RPG", "Simulation", "Sports", "Strategy", "Early Access", "Free to Play",
        "Violent", "Gore", "Sexual Content", "Nudity", "Education", "Software Training",
        "Utilities", "Design & Illustration", "Animation & Modeling", "Video Production",
        "Audio Production", "Photo Editing", "Game Development", "Web Publishing",
        "Accounting", "Documentary", "Tutorial"
    };

    private static final HttpClient CLIENTE = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Registro em construção: cada fase preenche uma parte. */
    private static class Bruto {
        String nome = "";
        String desenvolvedora = "";
        String faixaJogadores = "<20K";
        LocalDate data;
        Set<String> generos = new LinkedHashSet<>();
        float preco;
        int positivas;
        int negativas;
        boolean temSteamSpy;
    }

    public static void main(String[] args) throws Exception {
        String saida = args.length > 0 ? args[0] : "dados/steam.csv";
        int limite = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

        long inicio = System.currentTimeMillis();
        Map<Integer, Bruto> base = new ConcurrentHashMap<>();

        System.out.println("=".repeat(70));
        System.out.println("  COLETOR DA BASE STEAM");
        System.out.println("=".repeat(70));

        faseA(base);
        faseB(base);
        faseC(base, limite);
        escrever(base, saida);

        long seg = (System.currentTimeMillis() - inicio) / 1000;
        System.out.println("\nConcluído em " + (seg / 60) + "min " + (seg % 60) + "s.");
    }

    // ===================================================== FASE A · SteamSpy

    /**
     * Percorre as páginas de request=all até a API parar de responder.
     * Cada página traz 1000 jogos com os dados quantitativos.
     */
    @SuppressWarnings("unchecked")
    private static void faseA(Map<Integer, Bruto> base) {
        System.out.println("\n[A] SteamSpy — dados quantitativos");
        int pagina = 0;

        while (pagina < 200) {
            String corpo = baixar("https://steamspy.com/api.php?request=all&page=" + pagina, 3);
            if (corpo == null) break;

            Object raiz = Json.analisar(corpo);
            if (!(raiz instanceof Map)) break;

            Map<String, Object> mapa = (Map<String, Object>) raiz;
            if (mapa.isEmpty()) break;

            for (Map.Entry<String, Object> e : mapa.entrySet()) {
                int appid;
                try { appid = Integer.parseInt(e.getKey()); } catch (NumberFormatException x) { continue; }

                Bruto b = base.computeIfAbsent(appid, k -> new Bruto());
                b.temSteamSpy = true;
                b.nome = Json.texto(e.getValue(), "name", "");
                b.desenvolvedora = Json.texto(e.getValue(), "developer", "");
                b.positivas = (int) Json.numero(e.getValue(), "positive", 0);
                b.negativas = (int) Json.numero(e.getValue(), "negative", 0);
                // O preço vem em centavos de dólar; 0 significa gratuito.
                b.preco = (float) (Json.numero(e.getValue(), "price", 0) / 100.0);
                b.faixaJogadores = faixaDeProprietarios(Json.texto(e.getValue(), "owners", ""));
            }

            pagina++;
            if (pagina % 20 == 0) System.out.println("    página " + pagina + " · " + base.size() + " jogos");
        }
        System.out.println("    " + base.size() + " jogos em " + pagina + " páginas");
    }

    /**
     * Converte a faixa do SteamSpy ("10,000,000 .. 20,000,000") no limite
     * inferior compacto ("10M+"), que cabe no campo de tamanho fixo de 8 bytes.
     */
    private static String faixaDeProprietarios(String texto) {
        if (texto == null || texto.isEmpty()) return "<20K";
        String[] partes = texto.split("\\.\\.");
        long inferior;
        try {
            inferior = Long.parseLong(partes[0].replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return "<20K";
        }
        if (inferior <= 0) return "<20K";
        if (inferior >= 1_000_000) return (inferior / 1_000_000) + "M+";
        if (inferior >= 1_000) return (inferior / 1_000) + "K+";
        return inferior + "+";
    }

    // ======================================================= FASE B · gêneros

    /** Uma chamada por gênero; cada resposta lista todos os jogos daquele gênero. */
    @SuppressWarnings("unchecked")
    private static void faseB(Map<Integer, Bruto> base) {
        System.out.println("\n[B] SteamSpy — gêneros");
        int marcados = 0;

        for (String genero : GENEROS) {
            String url = "https://steamspy.com/api.php?request=genre&genre="
                    + java.net.URLEncoder.encode(genero, StandardCharsets.UTF_8);
            String corpo = baixar(url, 2);
            if (corpo == null) { System.out.println("    " + genero + ": falhou"); continue; }

            Object raiz = Json.analisar(corpo);
            if (!(raiz instanceof Map)) continue;

            int n = 0;
            for (String chave : ((Map<String, Object>) raiz).keySet()) {
                try {
                    Bruto b = base.get(Integer.parseInt(chave));
                    if (b != null) { b.generos.add(genero); n++; marcados++; }
                } catch (NumberFormatException ignorado) { }
            }
            System.out.println("    " + padDir(genero, 24) + n);
        }
        System.out.println("    " + marcados + " marcações de gênero");
    }

    // ========================================================= FASE C · datas

    /**
     * Pagina a busca da loja (filtro "Jogos") coletando appid, nome oficial e
     * data de lançamento. É a fase mais longa: são milhares de páginas de 100.
     */
    private static void faseC(Map<Integer, Bruto> base, int limite) throws Exception {
        System.out.println("\n[C] Loja Steam — datas de lançamento");

        int total = totalDaBusca();
        if (total <= 0) { System.out.println("    não foi possível ler o total"); return; }
        total = Math.min(total, limite);
        int paginas = (int) Math.ceil(total / (double) POR_PAGINA_BUSCA);
        System.out.println("    " + total + " jogos · " + paginas + " páginas de " + POR_PAGINA_BUSCA);

        ExecutorService piscina = Executors.newFixedThreadPool(THREADS_BUSCA);
        AtomicInteger prontas = new AtomicInteger();
        AtomicInteger comData = new AtomicInteger();

        List<java.util.concurrent.Future<?>> tarefas = new ArrayList<>();
        for (int p = 0; p < paginas; p++) {
            final int inicio = p * POR_PAGINA_BUSCA;
            tarefas.add(piscina.submit(() -> {
                int achados = lerPaginaDeBusca(base, inicio);
                comData.addAndGet(achados);
                int feitas = prontas.incrementAndGet();
                if (feitas % 100 == 0) {
                    System.out.println("    " + feitas + "/" + paginas + " páginas · "
                            + comData.get() + " datas");
                }
            }));
        }

        for (var t : tarefas) { try { t.get(); } catch (Exception ignorado) { } }
        piscina.shutdown();
        piscina.awaitTermination(10, TimeUnit.MINUTES);
        System.out.println("    " + comData.get() + " datas coletadas");
    }

    private static int totalDaBusca() {
        String corpo = baixar(urlBusca(0), 3);
        if (corpo == null) return -1;
        return (int) Json.numero(Json.analisar(corpo), "total_count", -1);
    }

    private static String urlBusca(int inicio) {
        return "https://store.steampowered.com/search/results/?query&start=" + inicio
             + "&count=" + POR_PAGINA_BUSCA
             + "&category1=998"          // apenas jogos (exclui DLC, trilhas sonoras e software)
             + "&infinite=1&json=1&ignore_preferences=1&l=english&cc=us";
    }

    private static final Pattern P_LINHA  = Pattern.compile("(?s)<a href=\"https://store\\.steampowered\\.com/app/.*?</a>");
    private static final Pattern P_APPID  = Pattern.compile("data-ds-appid=\"(\\d+)\"");
    private static final Pattern P_TITULO = Pattern.compile("(?s)<span class=\"title\">(.*?)</span>");
    private static final Pattern P_DATA   = Pattern.compile("(?s)search_released[^>]*>(.*?)</div>");

    /** Lê uma página de resultados e preenche nome e data dos jogos encontrados. */
    private static int lerPaginaDeBusca(Map<Integer, Bruto> base, int inicio) {
        String corpo = baixar(urlBusca(inicio), 3);
        if (corpo == null) return 0;

        Object raiz = Json.analisar(corpo);
        String html = Json.texto(raiz, "results_html", "");
        if (html.isEmpty()) return 0;

        int achados = 0;
        Matcher linhas = P_LINHA.matcher(html);
        while (linhas.find()) {
            String linha = linhas.group();

            Matcher mApp = P_APPID.matcher(linha);
            if (!mApp.find()) continue;
            int appid;
            try { appid = Integer.parseInt(mApp.group(1)); } catch (NumberFormatException e) { continue; }

            Bruto b = base.get(appid);
            if (b == null) continue;            // não está no SteamSpy: registro ficaria incompleto

            Matcher mData = P_DATA.matcher(linha);
            if (!mData.find()) continue;
            LocalDate data = lerData(limparHtml(mData.group(1)));
            if (data == null) continue;         // sem data ou "em breve": descartado
            b.data = data;

            Matcher mTitulo = P_TITULO.matcher(linha);
            if (mTitulo.find()) {
                String titulo = limparHtml(mTitulo.group(1));
                if (!titulo.isEmpty()) b.nome = titulo;
            }
            achados++;
        }
        return achados;
    }

    /** Formatos que a loja usa: "21 Aug, 2012", "Aug 2012" e, às vezes, só o ano. */
    private static LocalDate lerData(String texto) {
        String t = texto.trim();
        if (t.isEmpty()) return null;

        DateTimeFormatter[] formatos = {
            DateTimeFormatter.ofPattern("d MMM, uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH)
        };
        for (DateTimeFormatter f : formatos) {
            try { return LocalDate.parse(t, f); } catch (Exception ignorado) { }
        }
        try {
            return LocalDate.parse("1 " + t, DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH));
        } catch (Exception ignorado) { }
        if (t.matches("\\d{4}")) return LocalDate.of(Integer.parseInt(t), 1, 1);
        return null;
    }

    // ============================================================== ESCRITA

    /** Grava o CSV apenas com os registros completos: com data e com dados do SteamSpy. */
    private static void escrever(Map<Integer, Bruto> base, String caminho) throws Exception {
        System.out.println("\n[D] Gravando " + caminho);

        File arquivo = new File(caminho);
        if (arquivo.getParentFile() != null) arquivo.getParentFile().mkdirs();

        List<Map.Entry<Integer, Bruto>> completos = new ArrayList<>();
        for (Map.Entry<Integer, Bruto> e : base.entrySet()) {
            Bruto b = e.getValue();
            if (b.temSteamSpy && b.data != null && !b.nome.isBlank()) completos.add(e);
        }
        // Ordem decrescente de avaliações: a base sai "embaralhada" para as demais
        // chaves, que é o cenário honesto para demonstrar a ordenação externa.
        completos.sort(Comparator.comparingInt((Map.Entry<Integer, Bruto> e) -> e.getValue().positivas).reversed());

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/uuuu");
        int gravados = 0;

        try (BufferedWriter saida = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(arquivo), StandardCharsets.UTF_8), 1 << 16)) {

            saida.write("nome,desenvolvedora,faixa_jogadores,data_lancamento,generos,preco,"
                      + "avaliacoes_positivas,avaliacoes_negativas");
            saida.newLine();

            for (Map.Entry<Integer, Bruto> e : completos) {
                Bruto b = e.getValue();
                saida.write(csv(b.nome));                       saida.write(',');
                saida.write(csv(b.desenvolvedora));             saida.write(',');
                saida.write(csv(b.faixaJogadores));             saida.write(',');
                saida.write(b.data.format(fmt));                saida.write(',');
                saida.write(csv(String.join(";", b.generos)));  saida.write(',');
                saida.write(String.format(Locale.US, "%.2f", b.preco)); saida.write(',');
                saida.write(String.valueOf(b.positivas));       saida.write(',');
                saida.write(String.valueOf(b.negativas));
                saida.newLine();
                gravados++;
            }
        }

        System.out.println("    " + gravados + " jogos completos gravados");
        System.out.println("    " + (base.size() - gravados) + " descartados (sem data ou sem dados)");
        System.out.println("    " + String.format(Locale.US, "%.1f", arquivo.length() / 1024.0 / 1024.0) + " MB");
    }

    /** Escapa um campo de CSV: aspas duplicadas e aspas ao redor quando necessário. */
    private static String csv(String valor) {
        String v = valor == null ? "" : valor.replace('\n', ' ').replace('\r', ' ').trim();
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    // ============================================================= UTILITÁRIOS

    /** GET com tentativas e espera progressiva. Devolve null se todas falharem. */
    private static String baixar(String url, int tentativas) {
        for (int i = 0; i < tentativas; i++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", UA)
                        .timeout(Duration.ofSeconds(30))
                        .GET().build();
                HttpResponse<String> resp = CLIENTE.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() == 200) return resp.body();
                if (resp.statusCode() == 429) Thread.sleep(5000L * (i + 1));   // pedido de calma
            } catch (Exception e) {
                try { Thread.sleep(1000L * (i + 1)); } catch (InterruptedException ignorado) { }
            }
        }
        return null;
    }

    /** Remove marcação e resolve as entidades HTML que aparecem nos títulos. */
    private static String limparHtml(String texto) {
        String t = texto.replaceAll("<[^>]*>", " ");
        t = t.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
             .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
             .replace("&trade;", "").replace("&reg;", "").replace("&copy;", "");
        Matcher m = Pattern.compile("&#(\\d+);").matcher(t);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, String.valueOf((char) Integer.parseInt(m.group(1))));
        m.appendTail(sb);
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static String padDir(String s, int n) {
        return s.length() >= n ? s : s + " ".repeat(n - s.length());
    }
}
