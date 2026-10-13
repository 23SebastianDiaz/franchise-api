package com.juan.franchise.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.juan.franchise.domain.model.Product;
import com.juan.franchise.domain.port.FranchiseRepository;

import reactor.core.publisher.Mono;

@Service
public class CreateProductUseCase {

    private final FranchiseRepository repository;

    public CreateProductUseCase(FranchiseRepository repository) {
        this.repository = repository;
    }

    public Mono<Product> execute(String franchiseId, String branchId, String name, int stock) {
        return repository.saveProduct(franchiseId, branchId, new Product(UUID.randomUUID().toString(), name, stock));
    }
}
