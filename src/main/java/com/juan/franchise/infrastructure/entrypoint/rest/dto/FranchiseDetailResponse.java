package com.juan.franchise.infrastructure.entrypoint.rest.dto;

import java.util.Comparator;
import java.util.List;

import com.juan.franchise.domain.model.Branch;
import com.juan.franchise.domain.model.Franchise;
import com.juan.franchise.domain.model.Product;

public record FranchiseDetailResponse(String id, String name, List<BranchDetail> branches) {

    public record BranchDetail(String id, String name, List<ProductResponse> products) {
    }

    /** Branches and products are sorted by name so the output is stable between calls. */
    public static FranchiseDetailResponse from(Franchise franchise) {
        List<BranchDetail> branches = franchise.branches().stream()
                .sorted(Comparator.comparing(Branch::name).thenComparing(Branch::id))
                .map(b -> new BranchDetail(b.id(), b.name(), b.products().stream()
                        .sorted(Comparator.comparing(Product::name).thenComparing(Product::id))
                        .map(p -> new ProductResponse(p.id(), p.name(), p.stock()))
                        .toList()))
                .toList();
        return new FranchiseDetailResponse(franchise.id(), franchise.name(), branches);
    }
}
