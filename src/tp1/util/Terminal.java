package tp1.util;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Scanner;

import tp1.modelo.Jogo;

/**
 * Camada de interação com o terminal.
 *
 * Centraliza toda a leitura de teclado em métodos que NUNCA lançam exceção por
 * entrada inválida: se o usuário digitar "abc" onde se espera um número, a
 * pergunta é repetida. Isso é o que garante a robustez exigida no enunciado —
 * o programa não quebra com entrada malformada.
 *
 * Também força a saída em UTF-8, para que os acentos apareçam corretamente no
 * terminal do Windows.
 */
public class Terminal {

    /** Único leitor de teclado do sistema; UTF-8 para aceitar acentos. */
    private static final Scanner ENTRADA = new Scanner(System.in, StandardCharsets.UTF_8);
    /**
     * Saída própria em UTF-8. O terminal do Windows costuma usar outra página
     * de código, e escrever direto no descritor com UTF-8 evita que os acentos
     * saiam trocados.
     */
    public static final PrintStream SAIDA =
            new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);

    /** Largura das réguas e dos cabeçalhos, para o layout não desalinhar. */
    public static final int LARGURA = 78;

    // ------------------------------------------------------------- impressão

    /** Imprime uma linha de texto. */
    public static void imprimir(String texto) { SAIDA.println(texto); }

    /** Régua simples, para separar blocos dentro de uma tela. */
    public static void linha() { SAIDA.println("-".repeat(LARGURA)); }

    /** Régua dupla, usada nas bordas dos títulos de seção. */
    public static void linhaDupla() { SAIDA.println("=".repeat(LARGURA)); }

    /** Cabeçalho de seção. */
    public static void titulo(String texto) {
        SAIDA.println();
        linhaDupla();
        SAIDA.println("  " + texto.toUpperCase());
        linhaDupla();
    }

    /** Mensagens de retorno das operações, com prefixo padronizado. */
    public static void sucesso(String texto) { SAIDA.println("  [OK]    " + texto); }
    public static void erro(String texto)    { SAIDA.println("  [ERRO]  " + texto); }
    public static void aviso(String texto)   { SAIDA.println("  [AVISO] " + texto); }

    /** Cabeçalho das listagens de jogos. */
    public static void cabecalhoTabela() {
        SAIDA.printf("%-6s | %-36s | %10s | %7s | %8s | %9s | %4s%n",
                "ID", "NOME", "LANÇAMENTO", "FAIXA", "PREÇO", "AVAL.+", "APR.");
        linha();
    }

    /** Segura a tela até o usuário confirmar, para o resultado não sumir. */
    public static void pausar() {
        SAIDA.print("\n  Pressione ENTER para continuar...");
        ENTRADA.nextLine();
    }

    // --------------------------------------------------------------- leitura

    /** Lê uma linha de texto (pode ser vazia). */
    public static String lerTexto(String rotulo) {
        SAIDA.print("  " + rotulo + ": ");
        return ENTRADA.nextLine().trim();
    }

    /** Lê texto obrigatório: repete a pergunta enquanto vier vazio. */
    public static String lerTextoObrigatorio(String rotulo) {
        while (true) {
            String v = lerTexto(rotulo);
            if (!v.isEmpty()) return v;
            erro("Este campo não pode ficar em branco.");
        }
    }

    /** Lê texto permitindo manter o valor atual (ENTER em branco). */
    public static String lerTextoOuManter(String rotulo, String atual) {
        SAIDA.print("  " + rotulo + " [" + atual + "]: ");
        String v = ENTRADA.nextLine().trim();
        return v.isEmpty() ? atual : v;
    }

    /** Lê um inteiro dentro de uma faixa, repetindo enquanto a entrada for inválida. */
    public static int lerInteiro(String rotulo, int minimo, int maximo) {
        while (true) {
            String v = lerTexto(rotulo);
            try {
                int n = Integer.parseInt(v);
                if (n < minimo || n > maximo) {
                    erro("Informe um valor entre " + minimo + " e " + maximo + ".");
                    continue;
                }
                return n;
            } catch (NumberFormatException e) {
                erro("Valor inválido: digite um número inteiro.");
            }
        }
    }

    /** Lê um inteiro com valor padrão aplicado quando o usuário só aperta ENTER. */
    public static int lerInteiroOuPadrao(String rotulo, int padrao, int minimo, int maximo) {
        while (true) {
            SAIDA.print("  " + rotulo + " [" + padrao + "]: ");
            String v = ENTRADA.nextLine().trim();
            if (v.isEmpty()) return padrao;
            try {
                int n = Integer.parseInt(v);
                if (n < minimo || n > maximo) {
                    erro("Informe um valor entre " + minimo + " e " + maximo + ".");
                    continue;
                }
                return n;
            } catch (NumberFormatException e) {
                erro("Valor inválido: digite um número inteiro.");
            }
        }
    }

    /**
     * Lê um valor com casas decimais, aceitando vírgula ou ponto e recusando
     * negativos. ENTER em branco mantém o valor atual.
     */
    public static float lerFloatOuManter(String rotulo, float atual) {
        while (true) {
            SAIDA.print("  " + rotulo + " [" + String.format("%.2f", atual) + "]: ");
            String v = ENTRADA.nextLine().trim().replace(',', '.');
            if (v.isEmpty()) return atual;
            try {
                float f = Float.parseFloat(v);
                if (f < 0) { erro("O preço não pode ser negativo."); continue; }
                return f;
            } catch (NumberFormatException e) {
                erro("Valor inválido: digite um número (ex.: 49.90).");
            }
        }
    }

    /** Lê um inteiro maior ou igual a zero; ENTER em branco mantém o atual. */
    public static int lerInteiroNaoNegativoOuManter(String rotulo, int atual) {
        while (true) {
            SAIDA.print("  " + rotulo + " [" + atual + "]: ");
            String v = ENTRADA.nextLine().trim();
            if (v.isEmpty()) return atual;
            try {
                int n = Integer.parseInt(v);
                if (n < 0) { erro("O valor não pode ser negativo."); continue; }
                return n;
            } catch (NumberFormatException e) {
                erro("Valor inválido: digite um número inteiro.");
            }
        }
    }

    /** Lê uma data no formato dd/MM/yyyy, mantendo a atual se vier em branco. */
    public static LocalDate lerDataOuManter(String rotulo, LocalDate atual) {
        while (true) {
            SAIDA.print("  " + rotulo + " (dd/mm/aaaa) [" + atual.format(Jogo.FORMATO_DATA) + "]: ");
            String v = ENTRADA.nextLine().trim();
            if (v.isEmpty()) return atual;
            try {
                return LocalDate.parse(v, Jogo.FORMATO_DATA);
            } catch (Exception e) {
                erro("Data inválida. Use o formato dd/mm/aaaa (ex.: 21/08/2012).");
            }
        }
    }

    /** Pergunta de sim/não; ENTER assume o valor padrão. */
    public static boolean lerSimNao(String rotulo, boolean padrao) {
        while (true) {
            SAIDA.print("  " + rotulo + " (s/n) [" + (padrao ? "s" : "n") + "]: ");
            String v = ENTRADA.nextLine().trim().toLowerCase();
            if (v.isEmpty()) return padrao;
            if (v.startsWith("s")) return true;
            if (v.startsWith("n")) return false;
            erro("Responda com 's' ou 'n'.");
        }
    }

    /** Lê a lista de gêneros separada por ';'. */
    public static String[] lerListaOuManter(String rotulo, String[] atual) {
        String atualTexto = String.join(String.valueOf(Jogo.SEPARADOR_LISTA), atual);
        SAIDA.print("  " + rotulo + " (separe por ';') [" + atualTexto + "]: ");
        String v = ENTRADA.nextLine().trim();
        if (v.isEmpty()) return atual;

        String[] partes = v.split(String.valueOf(Jogo.SEPARADOR_LISTA));
        java.util.List<String> limpos = new java.util.ArrayList<>();
        for (String p : partes) {
            String t = p.trim();
            if (!t.isEmpty()) limpos.add(t);
        }
        return limpos.toArray(new String[0]);
    }

    /** Formata bytes em uma unidade legível (para as estatísticas). */
    public static String formatarBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
