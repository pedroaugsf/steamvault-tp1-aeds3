package tp1.modelo;

/**
 * Contrato mínimo que qualquer entidade precisa cumprir para ser armazenada
 * pela classe genérica {@link tp1.arquivo.ArquivoSequencial}.
 *
 * A ideia é isolar completamente a camada de persistência da camada de domínio:
 * o arquivo sequencial não sabe (e não precisa saber) o que é um "Jogo".
 * Ele só sabe pedir o id e converter o objeto de/para um vetor de bytes.
 */
public interface Registro {

    /** Identificador único do registro (chave primária, gerada pelo cabeçalho do arquivo). */
    int getId();

    /** Usado pelo arquivo para atribuir o id sequencial no momento da inserção. */
    void setId(int id);

    /** Serializa o objeto para o vetor de bytes que será gravado no arquivo. */
    byte[] toByteArray() throws java.io.IOException;

    /** Reconstrói o objeto a partir do vetor de bytes lido do arquivo. */
    void fromByteArray(byte[] dados) throws java.io.IOException;
}
