package com.juan.franchise.domain.model;

public record TopStockProduct(
        String branchId,
        String branchName,
        String productId,
        String productName,
        int stock) {
}
