package com.correcaoefd.exception;

import java.util.List;

/**
 * Lançada quando existem produtos no arquivo de erros (PE_02) que não existem no arquivo de produtos (PRODUTO).
 * A resposta inclui a lista para exibição e o conteúdo CSV para download.
 */
public class ProdutosErroNaoEncontradosException extends RuntimeException {

    private final List<ProdutoNaoEncontrado> produtos;

    public ProdutosErroNaoEncontradosException(String mensagem, List<ProdutoNaoEncontrado> produtos) {
        super(mensagem);
        this.produtos = produtos != null ? List.copyOf(produtos) : List.of();
    }

    public List<ProdutoNaoEncontrado> getProdutos() {
        return produtos;
    }

    public record ProdutoNaoEncontrado(String codigo, String descricao) {}
}
