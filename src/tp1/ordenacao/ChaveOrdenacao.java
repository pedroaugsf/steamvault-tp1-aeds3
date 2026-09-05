package tp1.ordenacao;

import java.util.Comparator;

import tp1.modelo.Jogo;

/**
 * Chaves disponíveis para a ordenação externa.
 *
 * É este enum que permite ordenar o arquivo por mais de um campo: o algoritmo
 * de ordenação externa não conhece nenhum atributo de Jogo, ele só recebe o
 * comparador escolhido aqui e o usa em todas as comparações. Trocar a chave
 * não muda uma linha da distribuição nem da intercalação.
 *
 * Os comparadores são escritos à mão, e não montados com os combinadores
 * prontos da biblioteca (Comparator.comparing, thenComparing, reversed), para
 * que toda a lógica de comparação seja de autoria do grupo. A interface
 * Comparator é usada apenas como o tipo que carrega a função.
 *
 * Todos desempatam pelo ID, o que garante uma ordenação determinística: dois
 * registros nunca ficam empatados e o resultado é sempre o mesmo, o que também
 * torna os testes reproduzíveis.
 */
public enum ChaveOrdenacao {

    /** Ordem crescente de ID — a ordem natural de inserção no arquivo. */
    ID("ID (padrão)", (a, b) -> compararInteiros(a.getId(), b.getId())),

    /**
     * Ordem alfabética do nome, ignorando maiúsculas/minúsculas.
     * A comparação caractere a caractere é feita abaixo, em compararTextos.
     */
    NOME("Nome do jogo (A-Z)", (a, b) -> {
        int c = compararTextos(a.getNome(), b.getNome());
        return (c != 0) ? c : compararInteiros(a.getId(), b.getId());
    }),

    /**
     * Ordem cronológica. Como a data é gravada como "epoch day", comparar duas
     * datas é comparar dois inteiros — nenhuma conversão é necessária.
     */
    DATA("Data de lançamento (mais antigo primeiro)", (a, b) -> {
        long da = a.getDataLancamento().toEpochDay();
        long db = b.getDataLancamento().toEpochDay();
        if (da != db) return (da < db) ? -1 : 1;
        return compararInteiros(a.getId(), b.getId());
    }),

    /** Ordem crescente de preço; jogos gratuitos aparecem primeiro. */
    PRECO("Preço (menor primeiro)", (a, b) -> {
        float pa = a.getPreco(), pb = b.getPreco();
        if (pa != pb) return (pa < pb) ? -1 : 1;
        return compararInteiros(a.getId(), b.getId());
    }),

    /** Ordem DECRESCENTE de avaliações positivas: os mais populares primeiro. */
    AVALIACOES("Avaliações positivas (maior primeiro)", (a, b) -> {
        int c = compararInteiros(b.getAvaliacoesPositivas(), a.getAvaliacoesPositivas());
        return (c != 0) ? c : compararInteiros(a.getId(), b.getId());
    });

    private final String descricao;
    private final Comparator<Jogo> comparador;

    ChaveOrdenacao(String descricao, Comparator<Jogo> comparador) {
        this.descricao = descricao;
        this.comparador = comparador;
    }

    public String getDescricao() { return descricao; }
    public Comparator<Jogo> getComparador() { return comparador; }

    // --------------------------------------------------------- comparações

    /**
     * Compara dois inteiros devolvendo -1, 0 ou 1.
     * A subtração direta (a - b) foi evitada de propósito: com valores grandes
     * ela estoura o int e inverte o sinal do resultado.
     */
    private static int compararInteiros(int a, int b) {
        if (a < b) return -1;
        if (a > b) return 1;
        return 0;
    }

    /**
     * Compara dois textos caractere a caractere, ignorando maiúsculas e
     * minúsculas. Se um for prefixo do outro, o mais curto vem antes.
     */
    private static int compararTextos(String a, String b) {
        String x = (a == null) ? "" : a;
        String y = (b == null) ? "" : b;

        int limite = Math.min(x.length(), y.length());
        for (int i = 0; i < limite; i++) {
            char ca = Character.toLowerCase(x.charAt(i));
            char cb = Character.toLowerCase(y.charAt(i));
            if (ca != cb) return (ca < cb) ? -1 : 1;
        }
        return compararInteiros(x.length(), y.length());
    }
}
