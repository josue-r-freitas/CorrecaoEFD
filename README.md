# Correção EFD – Estoque ICMS/IPI

Aplicação web (backend) em Java para correção de estoque sobre arquivo EFD ICMS/IPI, gerando um EFD de saída corrigido.

## Requisitos

- Java 17+
- Maven 3.6+
- SQL Server com banco **CORRECAO** e procedures `popular_PRODUTO_ESTOQUE_efd_2` e `CORRIGIR`

## Configuração do banco

Configure as variáveis de ambiente ou crie `src/main/resources/application-local.yml` (não versionado) com:

| Propriedade   | Descrição                          | Exemplo                          |
|---------------|------------------------------------|----------------------------------|
| `DB_HOST`     | Servidor SQL Server                | `DESKTOP-P13INJM\SQLEXPRESS`     |
| `DB_NAME`     | Nome do banco                      | `CORRECAO`                       |
| `DB_USER`     | Usuário                            | `app_elementar`                  |
| `DB_PASSWORD` | Senha                              | *(obrigatório definir)*          |
| `SERVER_PORT` | Porta da aplicação (opcional)      | `8080`                           |

Exemplo com variáveis de ambiente (PowerShell):

```powershell
$env:DB_PASSWORD = "sua_senha"
```

Exemplo `application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:sqlserver://DESKTOP-P13INJM\SQLEXPRESS;databaseName=CORRECAO
    username: app_elementar
    password: "!M19101993"
```

**Importante:** não commite credenciais no código. Use variáveis de ambiente ou arquivo local fora do controle de versão.

## Build

```bash
mvn clean package -DskipTests
```

## Execução

Com o perfil **local** (carrega `application-local.yml` com credenciais do banco):

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

Sem perfil (usa variáveis de ambiente ou `application.yml`):

```bash
mvn spring-boot:run
```

Ou com o JAR gerado:

```bash
java -jar target/correcao-efd-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
```

A API e a interface web estarão em `http://localhost:8080` (ou na porta configurada).

## Interface web (Etapa 2 – Frontend)

Abra no navegador **http://localhost:8080/** (ou **http://localhost:8080/index.html**). A tela permite:

1. **Importar erros** – selecionar o Excel de erros e clicar em "Importar erros".
2. **Importar produtos** – selecionar o Excel de produtos e clicar em "Importar produtos".
3. **Importar EFD** – selecionar o arquivo EFD (.txt) e clicar em "Importar EFD".
4. **Executar correção** – após os três arquivos importados, clicar em "Executar correção e baixar EFD". O arquivo EFD corrigido será gerado e o download iniciado automaticamente.

Cada etapa exibe mensagem de sucesso (com quantidade de registros) ou de erro. O nome do arquivo EFD importado é usado para gerar o nome do arquivo corrigido (sufixo "CORRIGIDO").

## API REST (Etapa 1 – Backend)

### Importação (tabelas são esvaziadas antes de cada importação)

| Método | Endpoint               | Descrição                          | Body / Parâmetros      |
|--------|------------------------|------------------------------------|------------------------|
| POST   | `/api/importar/erros`  | Importa Excel de erros → PE_02     | `arquivo` (multipart)  |
| POST   | `/api/importar/produtos` | Importa Excel de produtos → PRODUTO | `arquivo` (multipart) |
| POST   | `/api/importar/efd`    | Importa arquivo EFD → efd_2 (cada linha na coluna `linha`) | `arquivo` (multipart) |

### Correção

| Método | Endpoint      | Descrição                                                                 | Parâmetros (query)        |
|--------|---------------|----------------------------------------------------------------------------|---------------------------|
| POST   | `/api/corrigir` | Executa `popular_PRODUTO_ESTOQUE_efd_2`, depois `CORRIGIR('efd_2')`, retorna arquivo EFD corrigido para download | `nomeArquivoEfd` (opcional) – nome do EFD importado (ex.: EFD-FEV-2026.txt) |

- O resultado da procedure **CORRIGIR** é lido pelo **result set**; cada linha do result set vira uma linha do arquivo de saída.
- Nome do arquivo de download: nome original + sufixo **CORRIGIDO** (ex.: `EFD-FEV-2026CORRIGIDO.txt`).
- A última linha do arquivo termina com quebra de linha (ENTER).

## Teste com Postman

1. **Importar erros:** POST `http://localhost:8080/api/importar/erros`, body form-data, chave `arquivo`, tipo File, selecione o Excel de erros.
2. **Importar produtos:** POST `http://localhost:8080/api/importar/produtos`, body form-data, chave `arquivo`, tipo File, selecione o Excel de produtos.
3. **Importar EFD:** POST `http://localhost:8080/api/importar/efd`, body form-data, chave `arquivo`, tipo File, selecione o arquivo EFD (.txt).
4. **Executar correção e baixar EFD:** POST `http://localhost:8080/api/corrigir?nomeArquivoEfd=EFD-FEV-2026.txt` (ou sem parâmetro). Em “Send and Download” salve o arquivo e verifique o nome e a última linha com ENTER.

## Estrutura esperada no banco

- **PE_02:** colunas para código, descrição, quantidade (ajuste no código se os nomes forem diferentes, ex.: CODIGO, DESCRICAO, QUANTIDADE).
- **PRODUTO:** colunas para produto, descrição, unid, NCM, valor unit. (idem).
- **efd_2:** coluna **linha** (texto) – cada linha do arquivo EFD em um registro.
- Procedures: `popular_PRODUTO_ESTOQUE_efd_2` (sem parâmetros), `CORRIGIR(@nome_tabela)` que retorna result set com as linhas do EFD corrigido (primeira coluna = conteúdo da linha).

## Encoding EFD

Por padrão é usado **ISO-8859-1**. Para alterar: `efd.encoding` em `application.yml` (ex.: `UTF-8`).
