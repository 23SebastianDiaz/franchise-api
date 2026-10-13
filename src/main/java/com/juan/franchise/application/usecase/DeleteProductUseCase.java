package com.juan.franchise.application.usecase;

import org.springframework.stereotype.Service;

import com.juan.franchise.domain.port.FranchiseRepository;

import reactor.core.publisher.Mono;

@Service
public class DeleteProductUseCase {

    private final FranchiseRepository repository;

    public DeleteProductUseCase(FranchiseRepository repository) {
        this.repository = repository;
    }

    public Mono<Void> execute(String franchiseId, String branchId, String productId) {
        return repository.deleteProduct(franchiseId, branchId, productId);
    }
}
