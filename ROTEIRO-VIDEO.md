# Roteiro do vídeo — TP1 (máx. 10 minutos)

Sugestão de sequência. Deixe o terminal aberto na pasta do projeto e o site aberto em outra
aba para os trechos visuais.

---

## 0:00 — 0:50 · Abertura e escolha da base

- Nome, matrícula, disciplina, etapa.
- **Por que a Steam:** a base cobre os cinco tipos de campo exigidos sem inventar coluna.
- Mostrar a tabela de mapeamento (README §2 ou a seção do site): fixo → `faixaJogadores`,
  variável → `nome` e `desenvolvedora`, data → `dataLancamento`, lista → `generos` com `;`,
  inteiro/float → `avaliacoesPositivas`/`preco`.
- Citar que o `id` é gerado pelo cabeçalho do arquivo, não pelo CSV.
- **Diferencial:** a base não veio de um dataset pronto. Um coletor próprio cruza três fontes
  públicas pelo `appid` — SteamSpy para desenvolvedora, preço e avaliações; uma chamada por
  gênero para a lista de gêneros; e a busca da loja Steam para a data de lançamento, que
  nenhuma API entrega em massa.

## 0:50 — 2:10 · Decisões de implementação

Falar sobre código, não ler código. Quatro pontos:

1. **Layout do registro.** Abrir `ArquivoSequencial.java` no comentário do topo: cabeçalho
   com `int ultimoId`, e cada registro como lápide + tamanho + bytes. Explicar que o `tamanho`
   é mantido nos registros mortos — é ele que permite pular sem desserializar.
2. **Data como `int`.** Em `Jogo.toByteArray()`: o *epoch day* custa 4 bytes em vez dos ~10
   de uma string, e a comparação vira uma subtração de inteiros.
3. **`ArquivoSequencial<T extends Registro>` é genérico.** A persistência não sabe o que é um
   jogo; só sabe pedir o id e converter para bytes. Trocar a entidade não toca no CRUD.
4. **Carga em lote.** Com dezenas de milhares de linhas, o `create()` registro a registro
   custa minutos: dois posicionamentos e uma escrita sem buffer por linha. O `Lote` mantém o
   contador de ids na memória e grava o cabeçalho uma vez só, no fechamento — mesmo resultado
   no disco, muito mais rápido.

> Se sobrar tempo, mostrar o inspetor de registro do site: clicar num campo e ver os bytes
> correspondentes acenderem no dump hexadecimal.

## 2:10 — 4:30 · Demonstração do CRUD

```
executar.bat
```

1. **Opção 1 — carga.** ENTER para o CSV padrão. Mostrar a contagem final e o tempo.
2. **Opção 8 — estatísticas.** Registros válidos, 0 lápides, tamanho do arquivo.
3. **Opção 2 — listar.** Uma página, mostrar a navegação.
4. **Opção 3 — buscar por ID.** Buscar um id baixo e depois um id alto, e comparar os tempos:
   a busca é O(n), então o id alto demora mais. É a limitação que o TP2 resolve com índice.
5. **Opção 6 — update que MANTÉM o tamanho.** Editar um campo trocando por texto de mesmo
   comprimento. O sistema informa: *"registro regravado no MESMO lugar"*. Voltar em 8 e
   mostrar que o arquivo não cresceu e não há lápide.
6. **Opção 6 — update que MUDA o tamanho.** Acrescentar palavras ao nome. O sistema informa:
   *"o registro antigo recebeu lápide e a nova versão foi gravada no FIM"*.
7. **Opção 7 — excluir.** Apagar dois ou três ids.
8. **Opção 8 de novo.** Mostrar as lápides e o **percentual de espaço desperdiçado**. Esse
   número é o gancho para a próxima parte.

## 4:30 — 7:30 · Ordenação externa

**Opção 9.** Explicar cada parâmetro enquanto digita:

- **N (caminhos)** = quantos arquivos temporários são intercalados por vez → use 4.
- **M (registros em memória)** = o tamanho da memória primária simulada → use algo que dê
  algumas dezenas de blocos (com uma base grande, 2000 é um bom número).
- **Chave** = escolher `2` (nome), para mostrar que não está preso ao id.
- **Seleção por substituição** = responder `s`.

Ao terminar, ler o relatório na tela: blocos iniciais, média de registros por bloco, passadas
de intercalação, lápides eliminadas e espaço recuperado.

Depois:

- **Opção 2** — a base agora em ordem alfabética.
- **Opção 8** — 0 lápides, arquivo menor.
- **Opção 3** — provar que o CRUD continua funcionando no arquivo ordenado.

**Repetir a opção 9 com `n` na seleção por substituição** (bloco fixo) e comparar os blocos e
as passadas. Explicar por quê: a seleção por substituição mantém M registros na fila de
prioridade e produz blocos de tamanho variável, ~2·M em média, aproveitando a ordem já
existente nos dados.

> Alternativa visual: usar o simulador do site, mexendo em N e M ao vivo e mostrando as
> passadas encolherem.

## 7:30 — 9:00 · Testes e resultados

```
testar.bat
```

- Deixar rolar até o resumo: **40 aprovados, 0 reprovados**.
- Apontar os testes que provam os dois casos de update.
- Apontar a seção 7: as 5 chaves × 2 modos, cada uma verificando **par a par** se o arquivo
  ficou ordenado, se as lápides sumiram e se nenhum registro foi perdido.
- Mostrar a tabela comparativa e comentar a chave em que o ganho da otimização encolhe — onde
  os dados estão embaralhados, a heurística tem menos de onde tirar proveito. A otimização
  não é gratuita, e isso está registrado em vez de escondido.

## 9:00 — 9:40 · Robustez e encerramento

- Digitar `abc` numa opção de menu para mostrar que o programa **não quebra**.
- Citar: parser de CSV próprio (aspas, vírgulas dentro de aspas, BOM), analisador de JSON
  próprio no coletor, gravação em arquivo auxiliar antes de substituir a base, temporários
  apagados no `finally`.
- Mostrar o site publicado por alguns segundos.
- Fechar com o roadmap: TP2 (Árvore B+, Hash, Lista invertida), TP3 (Huffman e LZW),
  TP4 (casamento de padrões, criptografia e relatório).

---

## Checklist antes de gravar

- [ ] `chcp 65001` já está nos `.bat` — confira se os acentos aparecem no seu terminal
- [ ] Apagar `dados/jogos.db` antes de começar, para a carga aparecer do zero
- [ ] Conferir que `dados/steam.csv` está no lugar (ou rodar o coletor antes)
- [ ] Aumentar a fonte do terminal — a leitura no vídeo importa
- [ ] Deixar `ArquivoSequencial.java` e `OrdenacaoExterna.java` abertos em abas do editor
- [ ] Testar o áudio antes de gravar os 10 minutos inteiros
