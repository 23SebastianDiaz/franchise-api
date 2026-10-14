package com.juan.franchise.application.usecase;

import org.springframework.stereotype.Service;

import com.juan.franchise.domain.model.Franchise;
import com.juan.franchise.domain.port.FranchiseRepository;

import reactor.core.publisher.Mono;

@Service
public class RenameFranchiseUseCase {

    private final FranchiseRepository repository;

    public RenameFranchiseUseCase(FranchiseRepository repository) {
        this.repository = repository;
    }

    public Mono<Franchise> execute(String franchiseId, String name) {
        return repository.renameFranchise(franchiseId, name);
    }
}
