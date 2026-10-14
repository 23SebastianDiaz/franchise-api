package com.juan.franchise.application.usecase;

import org.springframework.stereotype.Service;

import com.juan.franchise.domain.model.Branch;
import com.juan.franchise.domain.port.FranchiseRepository;

import reactor.core.publisher.Mono;

@Service
public class RenameBranchUseCase {

    private final FranchiseRepository repository;

    public RenameBranchUseCase(FranchiseRepository repository) {
        this.repository = repository;
    }

    public Mono<Branch> execute(String franchiseId, String branchId, String name) {
        return repository.renameBranch(franchiseId, branchId, name);
    }
}
