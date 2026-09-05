package tp1.coleta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analisador de JSON mínimo, escrito para o coletor.
 *
 * As APIs públicas usadas na coleta devolvem JSON, e o projeto não pode usar
 * bibliotecas externas — então aqui está um analisador recursivo descendente
 * que cobre exatamente o que o formato exige: objetos, vetores, strings (com
 * escapes e \\uXXXX), números, booleanos e null.
 *
 * O resultado é montado com tipos do próprio Java:
 *   objeto  -> Map&lt;String,Object&gt;   (LinkedHashMap, preserva a ordem)
 *   vetor   -> List&lt;Object&gt;
 *   string  -> String
 *   número  -> Double
 *   bool    -> Boolean
 *   null    -> null
 */
public class Json {

    private final String texto;
    private int pos;

    private Json(String texto) {
        this.texto = texto;
        this.pos = 0;
    }

    /** Ponto de entrada: converte o texto JSON em estruturas Java. */
    public static Object analisar(String texto) {
        Json j = new Json(texto);
        j.pularEspacos();
        Object valor = j.lerValor();
        return valor;
    }

    // -------------------------------------------------------------- valores

    private Object lerValor() {
        pularEspacos();
        if (pos >= texto.length()) return null;

        char c = texto.charAt(pos);
        switch (c) {
            case '{': return lerObjeto();
            case '[': return lerVetor();
            case '"': return lerTexto();
            case 't': pos += 4; return Boolean.TRUE;      // true
            case 'f': pos += 5; return Boolean.FALSE;     // false
            case 'n': pos += 4; return null;              // null
            default:  return lerNumero();
        }
    }

    private Map<String, Object> lerObjeto() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        pos++;                                    // consome '{'
        pularEspacos();
        if (pos < texto.length() && texto.charAt(pos) == '}') { pos++; return mapa; }

        while (pos < texto.length()) {
            pularEspacos();
            String chave = lerTexto();
            pularEspacos();
            pos++;                                // consome ':'
            mapa.put(chave, lerValor());
            pularEspacos();

            if (pos >= texto.length()) break;
            char c = texto.charAt(pos++);
            if (c == '}') break;                  // fim do objeto
            // se for ',', segue para o próximo par
        }
        return mapa;
    }

    private List<Object> lerVetor() {
        List<Object> lista = new ArrayList<>();
        pos++;                                    // consome '['
        pularEspacos();
        if (pos < texto.length() && texto.charAt(pos) == ']') { pos++; return lista; }

        while (pos < texto.length()) {
            lista.add(lerValor());
            pularEspacos();
            if (pos >= texto.length()) break;
            char c = texto.charAt(pos++);
            if (c == ']') break;
        }
        return lista;
    }

    /** Lê uma string entre aspas, resolvendo os escapes previstos no formato. */
    private String lerTexto() {
        StringBuilder sb = new StringBuilder();
        pos++;                                    // consome a aspa inicial

        while (pos < texto.length()) {
            char c = texto.charAt(pos++);
            if (c == '"') break;

            if (c != '\\') { sb.append(c); continue; }

            char e = texto.charAt(pos++);
            switch (e) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'u':                          // escape unicode de 4 dígitos
                    sb.append((char) Integer.parseInt(texto.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default: sb.append(e);             // \" \\ \/ e afins
            }
        }
        return sb.toString();
    }

    private Double lerNumero() {
        int inicio = pos;
        while (pos < texto.length() && "-+.eE0123456789".indexOf(texto.charAt(pos)) >= 0) pos++;
        try {
            return Double.valueOf(texto.substring(inicio, pos));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void pularEspacos() {
        while (pos < texto.length() && Character.isWhitespace(texto.charAt(pos))) pos++;
    }

    // ------------------------------------------------------------- atalhos

    /** Lê um campo de texto de um objeto, com valor padrão se ausente. */
    @SuppressWarnings("unchecked")
    public static String texto(Object objeto, String chave, String padrao) {
        if (!(objeto instanceof Map)) return padrao;
        Object v = ((Map<String, Object>) objeto).get(chave);
        if (v == null) return padrao;
        return String.valueOf(v);
    }

    /** Lê um campo numérico de um objeto, com valor padrão se ausente ou inválido. */
    @SuppressWarnings("unchecked")
    public static double numero(Object objeto, String chave, double padrao) {
        if (!(objeto instanceof Map)) return padrao;
        Object v = ((Map<String, Object>) objeto).get(chave);
        if (v instanceof Double d) return d;
        if (v instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (Exception e) { return padrao; }
        }
        return padrao;
    }
}
