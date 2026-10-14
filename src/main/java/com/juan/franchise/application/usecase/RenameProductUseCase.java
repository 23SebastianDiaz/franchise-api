package com.juan.franchise.application.usecase;

import org.springframework.stereotype.Service;

import com.juan.franchise.domain.model.Product;
import com.juan.franchise.domain.port.FranchiseRepository;

import reactor.core.publisher.Mono;

@Service
public class RenameProductUseCase {

    private final FranchiseRepository repository;

    public RenameProductUseCase(FranchiseRepository repository) {
        this.repository = repository;
    }

    public Mono<Product> execute(String franchiseId, String branchId, String productId, String name) {
        return repository.renameProduct(franchiseId, branchId, productId, name);
    }
}
