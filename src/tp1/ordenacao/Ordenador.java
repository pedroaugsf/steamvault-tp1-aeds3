package tp1.ordenacao;

import java.util.Comparator;
import java.util.List;

/**
 * Ordenação em MEMÓRIA PRIMÁRIA usada na fase de distribuição da ordenação
 * externa (quando o modo "bloco fixo" está selecionado).
 *
 * Implementação própria de Quicksort com:
 *  - pivô mediana-de-três (evita o pior caso O(n²) em entradas já ordenadas,
 *    situação muito comum aqui, já que o arquivo costuma estar quase ordenado
 *    por ID);
 *  - recursão apenas na menor partição (limita a pilha a O(log n));
 *  - troca para inserção direta em partições pequenas (< 12 elementos), onde
 *    ela é mais rápida que o quicksort.
 *
 * Nenhum método de ordenação pronto do Java é usado.
 */
public class Ordenador {

    private static final int LIMITE_INSERCAO = 12;

    /** Ordena a lista inteira segundo o comparador informado. */
    public static <T> void ordenar(List<T> lista, Comparator<T> comparador) {
        if (lista == null || lista.size() < 2) return;
        @SuppressWarnings("unchecked")
        T[] vetor = (T[]) lista.toArray();
        quicksort(vetor, 0, vetor.length - 1, comparador);
        for (int i = 0; i < vetor.length; i++) lista.set(i, vetor[i]);
    }

    private static <T> void quicksort(T[] v, int esq, int dir, Comparator<T> c) {
        while (esq < dir) {
            if (dir - esq < LIMITE_INSERCAO) {
                insercao(v, esq, dir, c);
                return;
            }
            int p = particionar(v, esq, dir, c);

            // Recursão na menor metade; a maior continua no laço (menos pilha).
            if (p - esq < dir - p) {
                quicksort(v, esq, p - 1, c);
                esq = p + 1;
            } else {
                quicksort(v, p + 1, dir, c);
                dir = p - 1;
            }
        }
    }

    /** Particionamento de Lomuto usando a mediana de três como pivô. */
    private static <T> int particionar(T[] v, int esq, int dir, Comparator<T> c) {
        int meio = esq + (dir - esq) / 2;

        // Ordena esq, meio e dir entre si e leva a mediana para a posição dir-1.
        if (c.compare(v[meio], v[esq]) < 0) trocar(v, meio, esq);
        if (c.compare(v[dir], v[esq]) < 0) trocar(v, dir, esq);
        if (c.compare(v[dir], v[meio]) < 0) trocar(v, dir, meio);
        trocar(v, meio, dir);

        T pivo = v[dir];
        int i = esq - 1;
        for (int j = esq; j < dir; j++) {
            if (c.compare(v[j], pivo) <= 0) {
                i++;
                trocar(v, i, j);
            }
        }
        trocar(v, i + 1, dir);
        return i + 1;
    }

    /** Ordenação por inserção direta, eficiente em faixas pequenas. */
    private static <T> void insercao(T[] v, int esq, int dir, Comparator<T> c) {
        for (int i = esq + 1; i <= dir; i++) {
            T chave = v[i];
            int j = i - 1;
            while (j >= esq && c.compare(v[j], chave) > 0) {
                v[j + 1] = v[j];
                j--;
            }
            v[j + 1] = chave;
        }
    }

    private static <T> void trocar(T[] v, int a, int b) {
        T tmp = v[a];
        v[a] = v[b];
        v[b] = tmp;
    }
}
