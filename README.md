# SteamVault — TP1 de AEDS III

Sistema em Java para **CRUD em arquivo binário sequencial** e **ordenação externa por
intercalação balanceada de N caminhos**, sobre o catálogo de jogos da Steam coletado das
APIs públicas.

> Trabalho Prático I — Algoritmos e Estruturas de Dados III
> Etapa 1: criação da base, manipulação de arquivo sequencial e ordenação externa.
>
> **Autores:** Pedro Augusto Silva Ferreira · Breno Moreira Cortez

---

## 1. Como executar

Requisito único: **JDK 17 ou superior** (desenvolvido e testado no JDK 21).
Nenhuma biblioteca externa é utilizada.

### Windows

```bat
compilar.bat      :: compila src\ para bin\
executar.bat      :: abre o menu principal
testar.bat        :: roda a bateria de testes automatizados
```

### Linux / macOS / Git Bash

```bash
chmod +x executar.sh
./executar.sh            # menu principal
./executar.sh testes     # bateria de testes automatizados
```

### Manualmente

```bash
javac -encoding UTF-8 -d bin $(find src -name "*.java")
java -cp bin tp1.app.Main
```

> **Acentuação no Windows:** os scripts já executam `chcp 65001`. Se rodar `java` na mão,
> execute `chcp 65001` antes para que os acentos apareçam corretamente.

---

## 2. A base de dados

O arquivo `dados/steam.csv` traz o catálogo de jogos da Steam. Ele **não foi baixado de um
dataset pronto**: é montado por um coletor próprio (`tp1.coleta.ColetorSteam`) que cruza
três fontes públicas pelo `appid`, porque nenhuma delas entrega todos os campos de uma vez.

| Fase | Fonte | O que traz | Custo |
|---|---|---|---|
| A | SteamSpy `request=all` | desenvolvedora, preço, avaliações positivas e negativas, faixa de proprietários | 87 páginas de 1000 |
| B | SteamSpy `request=genre` | a lista de gêneros de cada jogo | 1 chamada por gênero |
| C | Busca da loja Steam | nome oficial e **data de lançamento** | páginas de 100 resultados |

A fase C existe porque a data de lançamento é o campo que nenhuma API entrega em massa.
A busca da loja resolve isso: devolve 100 jogos por chamada, com data.

Ao final ficam no CSV apenas os jogos presentes nas três fases — registros completos.
Jogos ainda não lançados (sem data) são descartados.

Para recoletar do zero:

```bash
java -cp bin tp1.coleta.ColetorSteam dados/steam.csv
```

### Os cinco tipos de campo exigidos

| Requisito do enunciado | Campo | Como é gravado |
|---|---|---|
| String de tamanho **fixo** | `faixaJogadores` | 8 bytes, completados com espaços |
| String de tamanho **variável** | `nome`, `desenvolvedora` | `writeUTF` (2 bytes de tamanho + conteúdo) |
| **Data** | `dataLancamento` | `int` com o *epoch day* (4 bytes em vez de ~10) |
| **Lista com separador** | `generos` | string única com os itens separados por `;` |
| **Inteiro / Float** | `avaliacoesPositivas`, `avaliacoesNegativas` / `preco` | `writeInt` / `writeFloat` |

O campo `id` **é gerado pelo sistema**, não pelo CSV: vem do inteiro do cabeçalho do arquivo
binário, conforme o enunciado.

**Sobre `faixaJogadores`.** É a faixa de proprietários publicada pelo SteamSpy, reduzida ao
limite inferior: `10M+`, `500K+`, `<20K`. Curta e regular por natureza, cabe num campo de
largura fixa sem truncar nada — e é um dado real, não um código inventado para preencher o
requisito.

**Compatibilidade com outros CSVs.** O leitor localiza as colunas pelo *nome* do cabeçalho, e
cada campo aceita vários apelidos, em português e em inglês (`nome`/`name`, `preco`/`price`,
`data_lancamento`/`release_date`, `faixa_jogadores`/`owners`). Um dataset da Steam baixado do
Kaggle carrega sem alterar uma linha de código.

---

## 3. Estrutura do arquivo binário

```
CABEÇALHO (4 bytes)
┌──────────────────────────┐
│ int  ultimoIdUtilizado   │
└──────────────────────────┘

REGISTRO (repetido N vezes, tamanho variável)
┌────────┬─────────────────┬──────────────────────────┐
│ lápide │ int tamanho     │ byte[] dados do objeto   │
│ 1 byte │ 4 bytes         │ 'tamanho' bytes          │
└────────┴─────────────────┴──────────────────────────┘

lápide = ' '  registro válido
lápide = '*'  registro logicamente excluído
```

O campo `tamanho` é mantido mesmo nos registros excluídos — é ele que permite pular o
registro morto e continuar a varredura sequencial sem desserializá-lo.

**Dentro do vetor de bytes**, os campos aparecem nesta ordem exata:

```
int    id                       4 bytes
UTF    nome                     2 + n bytes
UTF    desenvolvedora           2 + n bytes
byte[] faixaJogadores           8 bytes (tamanho FIXO)
int    dataLancamento           4 bytes (epoch day)
UTF    generos ("A;B;C")        2 + n bytes
float  preco                    4 bytes
int    avaliacoesPositivas      4 bytes
int    avaliacoesNegativas      4 bytes
```

---

## 4. Funcionalidades do menu

```
BASE DE DADOS
 1 - Carregar base a partir de um arquivo CSV
 2 - Listar registros (paginado)
 3 - Buscar por ID
 4 - Buscar por nome

CRUD SEQUENCIAL
 5 - Incluir um novo jogo
 6 - Atualizar um jogo
 7 - Excluir um jogo

ARQUIVO
 8 - Estatísticas do arquivo binário
 9 - Ordenação externa (intercalação balanceada)
```

### CRUD

| Operação | Comportamento |
|---|---|
| **Create** | Lê o último id do cabeçalho, incrementa, regrava o cabeçalho e grava o registro **no fim** do arquivo. |
| **Read** | Varredura sequencial a partir do byte 4, pulando registros com lápide. Custo O(n) — a limitação que motiva os índices do TP2. |
| **Update** | Se o novo registro tem **exatamente o mesmo tamanho**, é regravado **no próprio lugar**. Se o tamanho **mudou**, o registro antigo recebe lápide `'*'` e a nova versão vai para o **fim do arquivo**, preservando o id. |
| **Delete** | Exclusão **lógica**: apenas troca a lápide para `'*'`. O espaço só é recuperado na ordenação externa. |

A opção **8 (estatísticas)** mostra em tempo real quantos bytes estão sendo desperdiçados por
registros mortos — é o indicador que justifica rodar a ordenação externa.

**Carga em lote.** O `create()` normal é correto, mas caro para dezenas de milhares de linhas:
cada inserção faz dois posicionamentos para acertar o cabeçalho e uma escrita sem buffer.
A carga do CSV usa `ArquivoSequencial.Lote`, que mantém o contador de ids na memória, escreve
por um fluxo com buffer e grava o cabeçalho uma única vez, no fechamento. O resultado no disco
é byte a byte idêntico ao de chamar `create()` em sequência.

### Ordenação externa

Recebe os dois parâmetros exigidos pelo enunciado:

- **N — número de caminhos**: quantos arquivos temporários são intercalados por vez;
- **M — número máximo de registros em memória primária**.

E duas escolhas adicionais implementadas como **otimização**:

- **Chave de ordenação**: `ID`, `Nome`, `Data de lançamento`, `Preço` ou `Avaliações`.
  O mesmo algoritmo funciona para qualquer uma — basta trocar o comparador injetado.
- **Modo de geração dos blocos**: bloco fixo ou **seleção por substituição** (bloco de
  tamanho variável).

#### Como funciona

**Fase 1 — Distribuição.** O arquivo é lido sequencialmente e quebrado em blocos ordenados
("runs"), distribuídos circularmente entre N arquivos temporários. Registros com lápide são
**descartados aqui** — é assim que a ordenação recupera o espaço perdido.

- *Bloco fixo*: lê M registros, ordena em memória com **Quicksort próprio** (mediana de três,
  recursão só na menor partição, inserção direta em partições < 12) e grava.
- *Seleção por substituição*: mantém M registros em uma **fila de prioridade própria** e vai
  substituindo o menor conforme grava. Registros que "não cabem" no bloco corrente recebem o
  número da rodada seguinte. Produz blocos de **2·M registros em média**.

**Fase 2 — Intercalação.** A cada passada, N arquivos de entrada são intercalados em N
arquivos de saída, escolhendo o menor entre N candidatos com a fila de prioridade
(**O(log N)** por registro, contra O(N) da comparação ingênua). O número de blocos cai por um
fator N a cada passada.

> O fim de um bloco dentro de um arquivo temporário é detectado de forma **implícita**: como
> todo bloco é crescente, se o próximo registro for menor que o anterior, um novo bloco
> começou. Isso dispensa qualquer marcador extra no arquivo.

**Fase 3 — Reescrita.** O bloco final é convertido de volta ao formato do arquivo de dados
(cabeçalho + lápide + tamanho + bytes). A gravação é feita em um arquivo auxiliar e só então
o original é substituído — se algo falhar no meio, a base antiga permanece íntegra.

As operações de CRUD seguintes acontecem no arquivo ordenado.

---

## 5. Testes

`testar.bat` (ou `./executar.sh testes`) roda **40 verificações automatizadas**, sem
interação: carga do CSV, os dois cenários de update, exclusão lógica, inclusão, ordenação
externa nas **5 chaves × 2 modos** (conferindo par a par se o arquivo ficou ordenado, se as
lápides sumiram e se nenhum registro foi perdido) e o CRUD depois da ordenação.

A base é reconstruída no mesmo estado antes de cada rodada — sem isso, a segunda rodada
receberia um arquivo já ordenado pela primeira e os números não seriam comparáveis.

Os resultados medidos estão na tabela publicada no site (seção *Medições*) e são reproduzidos
por qualquer execução de `testar.bat` sobre a mesma base.

---

## 6. Organização do código

```
src/tp1/
├── app/
│   ├── Main.java                 menu de terminal (camada de apresentação)
│   └── Testes.java               bateria de testes automatizados
├── modelo/
│   ├── Registro.java             contrato: id + toByteArray/fromByteArray
│   └── Jogo.java                 entidade e serialização dos campos
├── arquivo/
│   ├── ArquivoSequencial.java    CRUD genérico + carga em lote
│   └── CargaCSV.java             parser de CSV próprio + mapeamento de colunas
├── ordenacao/
│   ├── OrdenacaoExterna.java     distribuição, intercalação e reescrita
│   ├── FilaDePrioridade.java     heap binário próprio
│   ├── Ordenador.java            quicksort próprio (ordenação em memória)
│   └── ChaveOrdenacao.java       comparadores por ID, nome, data, preço, avaliações
├── coleta/                       ferramentas auxiliares, fora do sistema avaliado
│   ├── ColetorSteam.java         monta o CSV a partir das APIs públicas
│   ├── Json.java                 analisador de JSON mínimo, escrito para o coletor
│   └── PrepararSite.java         gera os dados que o site embute
└── util/
    └── Terminal.java             entrada/saída robusta no terminal
```

**Decisão de projeto.** `ArquivoSequencial<T extends Registro>` é **genérico**. A camada de
persistência não sabe o que é um "Jogo" — ela só sabe pedir o id e converter o objeto de/para
bytes. Trocar a entidade da base não exige tocar em nenhuma linha do CRUD.

Nenhuma estrutura pronta de ordenação, fila de prioridade ou análise de JSON do Java é usada.
Da biblioteca padrão entram apenas as classes de leitura/escrita de arquivo, de conversão
entre atributos e campos (`RandomAccessFile`, `DataInputStream`/`DataOutputStream`) e, no
coletor, o cliente HTTP — como o enunciado permite.

---

## 7. Robustez

- Toda leitura do teclado rejeita entrada inválida e repete a pergunta — o programa não quebra
  com `"abc"` onde se espera um número.
- Linhas malformadas do CSV são contadas e reportadas, sem interromper a carga.
- A ordenação externa grava em arquivo auxiliar antes de substituir a base.
- Os arquivos temporários são apagados mesmo se a ordenação falhar (bloco `finally`).
- Datas do CSV são aceitas em `dd/MM/yyyy`, `yyyy-MM-dd` e `Aug 21, 2012`.
- O parser de CSV trata aspas duplas, vírgulas dentro de aspas, aspas escapadas e BOM UTF-8.
- O coletor tenta de novo com espera progressiva e respeita o `429` das APIs.

---

## 8. Publicando o site

A pasta `site/` contém o explorador do catálogo: um único HTML estático, sem build, com a
busca, os gráficos, o inspetor de registro e o simulador de intercalação rodando no navegador.

Para regerar os dados que ele embute, depois de carregar a base:

```bash
java -cp bin tp1.coleta.PrepararSite dados/jogos.db 5000
```

A publicação é automática: o fluxo em `.github/workflows/publicar-site.yml` republica o
GitHub Pages a cada push que altere `site/`. Nada para rodar na mão.

**Site no ar:** https://pedroaugsf.github.io/steamvault-tp1-aeds3/

**Domínio próprio.** Um subdomínio gratuito de projetos como is-a.dev ou js.org pode apontar
para este Pages via CNAME. Para um domínio pago (`.com.br` no Registro.br, por exemplo), o
GitHub Pages aceita domínio customizado em *Settings → Pages → Custom domain*, com HTTPS
emitido automaticamente.

---

## 9. Próximas etapas

| Etapa | Conteúdo |
|---|---|
| TP2 | Arquivo indexado: Árvore B+, Hash extensível e Lista invertida |
| TP3 | Compactação com Huffman e LZW |
| TP4 | Casamento de padrões e criptografia + relatório final |
