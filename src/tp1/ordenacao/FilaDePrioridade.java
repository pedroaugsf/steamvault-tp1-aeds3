package tp1.ordenacao;

import java.util.Comparator;

/**
 * Fila de prioridade (heap binário de mínimo) implementada pelo grupo.
 *
 * É usada em dois pontos críticos da ordenação externa:
 *
 *  1. SELEÇÃO POR SUBSTITUIÇÃO (fase de distribuição): mantém M registros em
 *     memória e produz blocos ordenados de tamanho VARIÁVEL — em média 2x M —,
 *     reduzindo o número de blocos e, consequentemente, o número de passadas
 *     de intercalação.
 *
 *  2. INTERCALAÇÃO DE N CAMINHOS: escolher o menor entre N candidatos custa
 *     O(log N) com heap, contra O(N) da comparação ingênua.
 *
 * Nenhuma estrutura pronta do Java (PriorityQueue) é utilizada — a implementação
 * é toda do grupo, conforme exigido pelo enunciado.
 */
public class FilaDePrioridade<T> {

    private Object[] itens;              // heap armazenado em vetor, raiz no índice 0
    private int tamanho;
    private final Comparator<T> comparador;

    public FilaDePrioridade(int capacidadeInicial, Comparator<T> comparador) {
        this.itens = new Object[Math.max(4, capacidadeInicial)];
        this.tamanho = 0;
        this.comparador = comparador;
    }

    public int tamanho() { return tamanho; }
    public boolean vazia() { return tamanho == 0; }

    /** Insere um elemento e restaura a propriedade de heap subindo-o (O(log n)). */
    public void inserir(T elemento) {
        if (tamanho == itens.length) crescer();
        itens[tamanho] = elemento;
        subir(tamanho);
        tamanho++;
    }

    /** Consulta o menor elemento sem removê-lo. */
    @SuppressWarnings("unchecked")
    public T espiar() {
        return tamanho == 0 ? null : (T) itens[0];
    }

    /** Remove e devolve o menor elemento, reorganizando o heap (O(log n)). */
    @SuppressWarnings("unchecked")
    public T remover() {
        if (tamanho == 0) return null;
        T menor = (T) itens[0];
        tamanho--;
        itens[0] = itens[tamanho];
        itens[tamanho] = null;
        if (tamanho > 0) descer(0);
        return menor;
    }

    // ------------------------------------------------------------- internos

    /** Move o elemento da posição i para cima enquanto for menor que seu pai. */
    @SuppressWarnings("unchecked")
    private void subir(int i) {
        while (i > 0) {
            int pai = (i - 1) / 2;
            if (comparador.compare((T) itens[i], (T) itens[pai]) < 0) {
                trocar(i, pai);
                i = pai;
            } else break;
        }
    }

    /** Move o elemento da posição i para baixo enquanto for maior que algum filho. */
    @SuppressWarnings("unchecked")
    private void descer(int i) {
        while (true) {
            int esquerda = 2 * i + 1;
            int direita = 2 * i + 2;
            int menor = i;

            if (esquerda < tamanho && comparador.compare((T) itens[esquerda], (T) itens[menor]) < 0) {
                menor = esquerda;
            }
            if (direita < tamanho && comparador.compare((T) itens[direita], (T) itens[menor]) < 0) {
                menor = direita;
            }
            if (menor == i) break;
            trocar(i, menor);
            i = menor;
        }
    }

    private void trocar(int a, int b) {
        Object tmp = itens[a];
        itens[a] = itens[b];
        itens[b] = tmp;
    }

    private void crescer() {
        Object[] novo = new Object[itens.length * 2];
        System.arraycopy(itens, 0, novo, 0, itens.length);
        itens = novo;
    }
}
