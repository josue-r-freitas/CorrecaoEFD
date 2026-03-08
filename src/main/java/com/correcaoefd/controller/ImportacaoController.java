package com.correcaoefd.controller;

import com.correcaoefd.exception.ProdutosErroNaoEncontradosException;
import com.correcaoefd.service.CorrecaoService;
import com.correcaoefd.service.ImportacaoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ImportacaoController {

    private final ImportacaoService importacaoService;
    private final CorrecaoService correcaoService;
    private final JdbcTemplate jdbcTemplate;

    public ImportacaoController(ImportacaoService importacaoService, CorrecaoService correcaoService,
                                JdbcTemplate jdbcTemplate) {
        this.importacaoService = importacaoService;
        this.correcaoService = correcaoService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Testa a conexão com o banco. Use no navegador ou Postman (GET) para ver o erro real se falhar.
     * Ex.: GET http://localhost:8080/api/testar-conexao
     */
    @GetMapping("/testar-conexao")
    public ResponseEntity<Map<String, Object>> testarConexao() {
        try {
            jdbcTemplate.getDataSource().getConnection().close();
            return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "mensagem", "Conexão com o banco OK."
            ));
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            String mensagem = cause.getMessage() != null ? cause.getMessage() : e.toString();
            return ResponseEntity.status(500).body(Map.of(
                "sucesso", false,
                "mensagem", "Falha na conexão: " + mensagem,
                "classe", cause.getClass().getSimpleName()
            ));
        }
    }

    @PostMapping(value = "/importar/erros", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importarErros(@RequestParam("arquivo") MultipartFile arquivo) {
        try {
            int registros = importacaoService.importarErros(arquivo);
            return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "mensagem", "Arquivo de erros importado com sucesso.",
                "registros", registros
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "sucesso", false,
                "mensagem", "Erro ao importar: " + e.getMessage()
            ));
        }
    }

    @PostMapping(value = "/importar/produtos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importarProdutos(@RequestParam("arquivo") MultipartFile arquivo) {
        try {
            int registros = importacaoService.importarProdutos(arquivo);
            return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "mensagem", "Arquivo de produtos importado com sucesso.",
                "registros", registros
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "sucesso", false,
                "mensagem", "Erro ao importar: " + e.getMessage()
            ));
        }
    }

    @PostMapping(value = "/importar/efd", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importarEfd(@RequestParam("arquivo") MultipartFile arquivo) {
        try {
            int registros = importacaoService.importarEfd(arquivo);
            return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "mensagem", "Arquivo EFD importado com sucesso.",
                "registros", registros
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "sucesso", false,
                "mensagem", "Erro ao importar: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/corrigir")
    public ResponseEntity<?> executarCorrecao(
            @RequestParam(value = "nomeArquivoEfd", required = false) String nomeArquivoEfd) {
        try {
            List<String> linhas = correcaoService.executarCorrecao();
            if (linhas == null || linhas.isEmpty()) {
                return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                        "sucesso", false,
                        "mensagem", "Nenhuma linha retornada pela correção. Importe o arquivo EFD antes (POST /api/importar/efd) e certifique-se de ter importado erros e produtos. Depois execute a correção novamente."
                    ));
            }
            byte[] conteudo = correcaoService.gerarArquivoEfdCorrigido(linhas);
            String nomeDownload = correcaoService.nomeArquivoCorrigido(nomeArquivoEfd);

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeDownload + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(conteudo);
        } catch (ProdutosErroNaoEncontradosException e) {
            List<Map<String, String>> produtos = new ArrayList<>();
            StringBuilder csv = new StringBuilder("codigo;descricao\r\n");
            for (ProdutosErroNaoEncontradosException.ProdutoNaoEncontrado p : e.getProdutos()) {
                produtos.add(Map.of("codigo", p.codigo() != null ? p.codigo() : "", "descricao", p.descricao() != null ? p.descricao() : ""));
                csv.append(escapeCsv(p.codigo())).append(';').append(escapeCsv(p.descricao())).append("\r\n");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sucesso", false);
            body.put("mensagem", e.getMessage());
            body.put("produtos", produtos);
            body.put("arquivoCsv", csv.toString());
            body.put("nomeArquivoDownload", "produtos-erro-nao-encontrados.csv");
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                    "sucesso", false,
                    "mensagem", e.getMessage()
                ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                    "sucesso", false,
                    "mensagem", "Erro ao executar correção: " + e.getMessage()
                ));
        }
    }

    private static String escapeCsv(String valor) {
        if (valor == null) return "";
        if (valor.contains(";") || valor.contains("\"") || valor.contains("\r") || valor.contains("\n")) {
            return "\"" + valor.replace("\"", "\"\"") + "\"";
        }
        return valor;
    }
}
