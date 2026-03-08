package com.correcaoefd.service;

import com.correcaoefd.exception.ProdutosErroNaoEncontradosException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Service
public class CorrecaoService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${efd.encoding:ISO-8859-1}")
    private String efdEncoding;

    public CorrecaoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Garante que todos os produtos do arquivo de erros (PE_02) existem no arquivo de produtos (PRODUTO).
     * @throws ProdutosErroNaoEncontradosException se existir ao menos um produto em PE_02 que não está em PRODUTO
     */
    private void validarProdutosErroExistemEmProdutos() {
        String sql = "SELECT DISTINCT p2.CODIGO_ITEM, p2.DESCRICAO FROM [PE_02] p2 " +
            "LEFT JOIN [PRODUTO] pr ON p2.CODIGO_ITEM = pr.CODIGO_ITEM WHERE pr.CODIGO_ITEM IS NULL";
        List<ProdutosErroNaoEncontradosException.ProdutoNaoEncontrado> lista = jdbcTemplate.query(sql, (rs, rowNum) ->
            new ProdutosErroNaoEncontradosException.ProdutoNaoEncontrado(
                rs.getString(1),
                rs.getString(2)
            )
        );
        if (lista != null && !lista.isEmpty()) {
            throw new ProdutosErroNaoEncontradosException(
                "Existe(m) " + lista.size() + " produto(s) no arquivo de erros que não existem no arquivo de produtos. Faça o download da lista abaixo.",
                lista
            );
        }
    }

    /**
     * Verifica se o EFD em efd_2 contém o registro H010 na totalização do bloco |9900|.
     * Na totalização, o bloco 9900 deve ter uma linha no formato |9900|H010|quantidade|.
     * @throws IllegalStateException se o registro H010 não estiver na totalização do bloco 9900
     */
    private void validarRegistroH010NoBloco9900() {
        String sql = "SELECT linha FROM efd_2 ORDER BY id";
        List<String> linhas;
        try {
            linhas = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1));
        } catch (Exception e) {
            linhas = jdbcTemplate.query("SELECT linha FROM efd_2", (rs, rowNum) -> rs.getString(1));
        }
        if (linhas == null || linhas.isEmpty()) {
            throw new IllegalStateException("O EFD a ser corrigido está vazio. Importe o arquivo EFD (POST /api/importar/efd) antes de executar a correção.");
        }
        boolean encontrouBloco9900 = false;
        boolean encontrouH010NaTotalizacao = false;
        for (String linha : linhas) {
            if (linha == null) continue;
            String t = linha.trim();
            if (t.isEmpty()) continue;
            String[] parts = t.split("\\|", -1);
            if (parts.length >= 3) {
                String reg = parts[1].trim();
                if ("9900".equals(reg)) {
                    encontrouBloco9900 = true;
                    String regBlc = parts.length > 2 ? parts[2].trim() : "";
                    if ("H010".equals(regBlc)) {
                        encontrouH010NaTotalizacao = true;
                        break;
                    }
                }
            }
        }
        if (!encontrouBloco9900) {
            throw new IllegalStateException("O EFD a ser corrigido não contém o bloco |9900| (totalização).");
        }
        if (!encontrouH010NaTotalizacao) {
            throw new IllegalStateException("O EFD a ser corrigido não contém o registro H010 na totalização do bloco |9900|.");
        }
    }

    /**
     * Garante que não existe nenhum registro com valor unitário nulo na tabela PRODUTO_ESTOQUE_efd_2.
     * @throws IllegalStateException se existir ao menos um registro com valor unitário nulo
     */
    private void validarValorUnitarioNaoNulo() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM [PRODUTO_ESTOQUE_efd_2] WHERE vl_unit IS NULL",
            Integer.class
        );
        if (count != null && count > 0) {
            throw new IllegalStateException(
                "Não pode haver registro com valor unitário nulo na tabela PRODUTO_ESTOQUE_efd_2. Encontrado(s) " + count + " registro(s) com valor unitário nulo."
            );
        }
    }

    /**
     * Executa o fluxo: valida H010 no 9900, popular_PRODUTO_ESTOQUE_efd_2, valida valor unitário, depois CORRIGIR('efd_2')
     * e retorna as linhas do result set da procedure CORRIGIR.
     */
    @Transactional
    public List<String> executarCorrecao() {
        validarProdutosErroExistemEmProdutos();
        validarRegistroH010NoBloco9900();
        jdbcTemplate.execute("EXEC popular_PRODUTO_ESTOQUE_efd_2");
        validarValorUnitarioNaoNulo();

        List<String> linhas = new ArrayList<>();
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (CallableStatement cs = connection.prepareCall("EXEC CORRIGIR ?")) {
                cs.setString(1, "efd_2");
                // Procedure pode retornar primeiro update count(s) e depois o SELECT com as linhas.
                // Processar todos os resultados até encontrar o ResultSet com os dados.
                boolean hasResultSet = cs.execute();
                while (true) {
                    if (hasResultSet) {
                        try (ResultSet rs = cs.getResultSet()) {
                            if (rs != null) {
                                int colCount = rs.getMetaData().getColumnCount();
                                while (rs.next()) {
                                    String linha = colCount >= 1 ? rs.getString(1) : null;
                                    if (linha != null) {
                                        linhas.add(linha);
                                    }
                                }
                            }
                        }
                    } else {
                        if (cs.getUpdateCount() == -1) break;
                    }
                    hasResultSet = cs.getMoreResults();
                }
            }
            return null;
        });
        return linhas;
    }

    /**
     * Gera o conteúdo do arquivo EFD corrigido (cada linha + quebra de linha;
     * garante que a última linha termina com ENTER).
     */
    public byte[] gerarArquivoEfdCorrigido(List<String> linhas) throws Exception {
        Charset charset = Charset.forName(efdEncoding);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(out, charset)) {
            for (String linha : linhas) {
                writer.write(linha);
                writer.write("\r\n");
            }
        }
        return out.toByteArray();
    }

    /**
     * Gera o nome do arquivo de saída: nome original + "CORRIGIDO".
     * Ex.: EFD-FEV-2026.txt -> EFD-FEV-2026CORRIGIDO.txt
     */
    public String nomeArquivoCorrigido(String nomeArquivoEfdOriginal) {
        if (nomeArquivoEfdOriginal == null || nomeArquivoEfdOriginal.isBlank()) {
            return "EFD-CORRIGIDO.txt";
        }
        String base = nomeArquivoEfdOriginal.trim();
        int lastDot = base.lastIndexOf('.');
        if (lastDot > 0) {
            return base.substring(0, lastDot) + "CORRIGIDO" + base.substring(lastDot);
        }
        return base + "CORRIGIDO.txt";
    }
}
