package com.correcaoefd.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImportacaoService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${efd.encoding:ISO-8859-1}")
    private String efdEncoding;

    public ImportacaoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public int importarErros(MultipartFile file) throws Exception {
        esvaziarTabela("PE_02");
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = encontrarLinhaCabecalhoRow(sheet, "CÓDIGO", "DESCRIÇÃO", "QUANTIDADE");
            if (headerRow == null) {
                throw new IllegalArgumentException("Planilha de erros deve conter as colunas CÓDIGO, DESCRIÇÃO e QUANTIDADE");
            }
            int colCodigo = indiceColuna(headerRow, "CÓDIGO");
            int colDescricao = indiceColuna(headerRow, "DESCRIÇÃO");
            int colQuantidade = indiceColuna(headerRow, "QUANTIDADE");
            int rowNum = headerRow.getRowNum();
            String sql = "INSERT INTO [PE_02] (CODIGO_ITEM, DESCRICAO, QUANTIDADE) VALUES (?, ?, ?)";
            List<Object[]> batch = new ArrayList<>();
            for (int i = rowNum + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String codigo = getCellString(row, colCodigo);
                String descricao = getCellString(row, colDescricao);
                Double qtd = getCellNumeric(row, colQuantidade);
                if (codigo == null || codigo.isBlank()) continue;
                batch.add(new Object[]{codigo.trim(), descricao != null ? descricao.trim() : null, qtd != null ? qtd : 0});
            }
            if (batch.isEmpty()) return 0;
            jdbcTemplate.batchUpdate(sql, batch);
            return batch.size();
        }
    }

    @Transactional
    public int importarProdutos(MultipartFile file) throws Exception {
        esvaziarTabela("PRODUTO");
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = encontrarLinhaCabecalhoProdutosRow(sheet);
            if (headerRow == null) {
                throw new IllegalArgumentException("Planilha de produtos deve conter as colunas Produto, Descrição, Unid, NCM, Valor Unit.");
            }
            int colProduto = indiceColunaProdutos(headerRow, "Produto");
            int colDescricao = indiceColunaProdutos(headerRow, "Descrição");
            int colUnid = indiceColuna(headerRow, "Unid");
            int colNcm = indiceColuna(headerRow, "NCM");
            int colValor = indiceColunaValorUnit(headerRow);
            int rowNum = headerRow.getRowNum();
            String sql = "INSERT INTO [PRODUTO] (CODIGO_ITEM, DESCRICAO, UNIDADE, NCM, VL_UNIT) VALUES (?, ?, ?, ?, ?)";
            List<Object[]> batch = new ArrayList<>();
            for (int i = rowNum + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String produto = getCellString(row, colProduto);
                if (produto == null || produto.isBlank()) continue;
                String descricao = getCellString(row, colDescricao);
                String unid = getCellString(row, colUnid);
                String ncm = getCellString(row, colNcm);
                Double valorUnit = getCellNumeric(row, colValor);
                batch.add(new Object[]{
                    produto.trim(),
                    descricao != null ? descricao.trim() : null,
                    unid != null ? unid.trim() : null,
                    ncm != null ? ncm.trim() : null,
                    valorUnit != null ? valorUnit : null
                });
            }
            if (batch.isEmpty()) return 0;
            jdbcTemplate.batchUpdate(sql, batch);
            return batch.size();
        }
    }

    @Transactional
    public int importarEfd(MultipartFile file) throws Exception {
        esvaziarTabela("efd_2");
        Charset charset = Charset.forName(efdEncoding);
        List<String> linhas = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                linhas.add(line);
            }
        }
        if (linhas.isEmpty()) return 0;
        String sql = "INSERT INTO [efd_2] (linha) VALUES (?)";
        List<Object[]> batch = new ArrayList<>();
        for (String linha : linhas) {
            batch.add(new Object[]{linha});
        }
        jdbcTemplate.batchUpdate(sql, batch);
        return batch.size();
    }

    private void esvaziarTabela(String tabela) {
        jdbcTemplate.execute("DELETE FROM [" + tabela + "]");
    }

    private Row encontrarLinhaCabecalhoRow(Sheet sheet, String... colunas) {
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 10); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            boolean allMatch = true;
            for (String col : colunas) {
                int idx = indiceColuna(row, col);
                if (idx < 0) { allMatch = false; break; }
            }
            if (allMatch) return row;
        }
        return null;
    }

    private Row encontrarLinhaCabecalhoProdutosRow(Sheet sheet) {
        int maxRow = Math.min(sheet.getLastRowNum(), 25);
        for (int i = 0; i <= maxRow; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            if (indiceColunaProdutos(row, "Produto") >= 0 && indiceColunaProdutos(row, "Descrição") >= 0
                && indiceColuna(row, "Unid") >= 0 && indiceColuna(row, "NCM") >= 0
                && indiceColunaValorUnit(row) >= 0) {
                return row;
            }
        }
        return null;
    }

    /** Normaliza texto de cabeçalho para comparação (trim, espaço não-quebrável). */
    private static String normalizarCabecalho(String s) {
        if (s == null) return "";
        return s.trim().replace('\u00A0', ' ');
    }

    private int indiceColuna(Row headerRow, String nomeColuna) {
        String esperado = normalizarCabecalho(nomeColuna);
        for (int c = 0; c < 50; c++) {
            String cell = getCellString(headerRow, c);
            if (cell != null && normalizarCabecalho(cell).equalsIgnoreCase(esperado)) return c;
        }
        return -1;
    }

    /** Índice para coluna de produtos; aceita "Descrição" ou "Descricao". */
    private int indiceColunaProdutos(Row headerRow, String nomeColuna) {
        String esperado = normalizarCabecalho(nomeColuna);
        boolean ehDescricao = esperado.equalsIgnoreCase("Descrição") || esperado.equalsIgnoreCase("Descricao");
        for (int c = 0; c < 50; c++) {
            String cell = getCellString(headerRow, c);
            if (cell == null) continue;
            String norm = normalizarCabecalho(cell);
            if (norm.equalsIgnoreCase(esperado)) return c;
            if (ehDescricao && (norm.equalsIgnoreCase("Descricao") || norm.equalsIgnoreCase("Descrição"))) return c;
        }
        return -1;
    }

    private int indiceColunaValorUnit(Row headerRow) {
        for (int c = 0; c < 50; c++) {
            String cell = getCellString(headerRow, c);
            if (cell != null) {
                String norm = normalizarCabecalho(cell).toLowerCase();
                if (norm.startsWith("valor") && (norm.contains("unit") || norm.contains("unit."))) return c;
                if (norm.startsWith("valor")) return c;
            }
        }
        return -1;
    }

    private String getCellString(Row row, int col) {
        var cell = row.getCell(col);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    var val = cell.getCachedFormulaResultType();
                    if (val == org.apache.poi.ss.usermodel.CellType.STRING) yield cell.getStringCellValue();
                    if (val == org.apache.poi.ss.usermodel.CellType.NUMERIC) yield String.valueOf((long) cell.getNumericCellValue());
                } catch (Exception ignored) { }
                yield null;
            }
            default -> null;
        };
    }

    private Double getCellNumeric(Row row, int col) {
        var cell = row.getCell(col);
        if (cell == null) return null;
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
            String s = cell.getStringCellValue();
            if (s == null || s.isBlank()) return null;
            try {
                return Double.parseDouble(s.replace(",", "."));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
