# Guia de instalação – Correção EFD

Este guia explica como instalar **Java 17** e **Maven** no Windows para rodar a aplicação.

---

## Parte 1: Instalar Java 17

### Opção A – Pelo WinGet (recomendado no Windows 10/11)

1. Abra o **PowerShell** como administrador (clique direito no menu Iniciar → "Terminal (Admin)" ou "Windows PowerShell (Admin)").
2. Execute:
   ```powershell
   winget install EclipseAdoptium.Temurin.17.JDK
   ```
3. Aceite as permissões se solicitado. Ao final, feche e abra o terminal de novo e rode `java -version` para conferir.

### Opção B – Baixar pelo site

1. Use um destes links (página de downloads do Adoptium):
   - **Link direto JDK 17 Windows:** [https://adoptium.net/temurin/releases/?os=windows&package=jdk&version=17](https://adoptium.net/temurin/releases/?os=windows&package=jdk&version=17)
   - **Página geral de download:** [https://adoptium.net/download/](https://adoptium.net/download/)
   - **Instalação Windows (instruções + links):** [https://adoptium.net/installation/windows](https://adoptium.net/installation/windows)
2. Na página, escolha **Operating System: Windows**, **Architecture: x64**, **Package: JDK**, **Version: 17**.
3. Clique no botão **`.msi`** para baixar o instalador.

### Passo 1.2 – Instalar

1. Execute o arquivo `.msi` baixado.
2. Clique em **Next** até a tela **"Set JAVA_HOME variable"**.
3. Deixe marcada a opção **"Set JAVA_HOME variable"** (recomendado).
4. Continue e conclua a instalação.

### Passo 1.3 – Conferir no terminal

1. Feche e abra de novo o **PowerShell** ou **Prompt de Comando** (para carregar o novo PATH).
2. Rode:
   ```powershell
   java -version
   ```
3. Deve aparecer algo como: `openjdk version "17.x.x"`.  
   Se ainda aparecer Java 8, o PATH está apontando para a instalação antiga. Nesse caso:
   - Abra **Variáveis de ambiente** (busque por "variáveis de ambiente" no menu Iniciar).
   - Em **Variáveis do sistema**, edite **JAVA_HOME** e coloque o caminho da pasta do **JDK 17** (ex.: `C:\Program Files\Eclipse Adoptium\jdk-17.x.x`).
   - Em **Path**, garanta que exista `%JAVA_HOME%\bin` e que não haja outro caminho de Java antes dele.

---

## Parte 2: Instalar Maven

### Passo 2.1 – Baixar o Maven

1. Acesse: **https://maven.apache.org/download.cgi**
2. Em **Files**, baixe o **"Binary zip archive"** (ex.: `apache-maven-3.9.x-bin.zip`).

### Passo 2.2 – Extrair e colocar em uma pasta fixa

1. Extraia o ZIP para uma pasta sem espaços, por exemplo:
   - `C:\Programas\apache-maven-3.9.6`
2. Anote esse caminho (será o **MAVEN_HOME**).

### Passo 2.3 – Configurar variáveis de ambiente

1. No menu Iniciar, busque por **"Variáveis de ambiente"** e abra **"Editar as variáveis de ambiente do sistema"**.
2. Clique em **"Variáveis de ambiente..."**.
3. Em **Variáveis do sistema**, clique em **Novo**:
   - Nome: `MAVEN_HOME`
   - Valor: o caminho da pasta do Maven (ex.: `C:\Programas\apache-maven-3.9.6`)
4. Selecione a variável **Path** e clique em **Editar**.
5. Clique em **Novo** e adicione: `%MAVEN_HOME%\bin`
6. Confirme com **OK** em todas as janelas.

### Passo 2.4 – Conferir no terminal

1. Feche e abra de novo o PowerShell.
2. Rode:
   ```powershell
   mvn -version
   ```
3. Deve aparecer a versão do Maven e do Java (preferencialmente Java 17).

---

## Parte 3: Rodar a aplicação

### No diretório do projeto

1. Abra o PowerShell e vá até a pasta do projeto:
   ```powershell
   cd "C:\Users\josue\OneDrive\Documentos\TrabalhosExtras\CorrecaoEFD"
   ```
2. Compile e execute:
   ```powershell
   mvn spring-boot:run
   ```
3. Quando subir, abra no navegador: **http://localhost:8080**. Você verá a página inicial da aplicação com a lista de endpoints da API (importar erros, produtos, EFD e executar correção).

### Banco de dados (SQL Server)

- A aplicação espera um SQL Server em: `DESKTOP-P13INJM\SQLEXPRESS`, base **CORRECAO**.
- Certifique-se de que o SQL Server está rodando e que a base e o usuário/senha do `application.yml` existem e estão corretos.

---

## Resumo rápido

| Etapa | O que fazer |
|-------|-------------|
| 1 | Instalar JDK 17 (Adoptium) e conferir `java -version` |
| 2 | Baixar Maven (bin zip), extrair, criar MAVEN_HOME e adicionar `%MAVEN_HOME%\bin` ao Path |
| 3 | Fechar e abrir o terminal e testar `mvn -version` |
| 4 | Na pasta do projeto: `mvn spring-boot:run` |

Se em algum passo aparecer erro ou mensagem diferente do esperado, copie a mensagem e o comando que você usou para poder ajustar o próximo passo.
