package com.juan.franchise.application.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.juan.franchise.domain.model.Branch;
import com.juan.franchise.domain.port.FranchiseRepository;

import reactor.core.publisher.Mono;

@Service
public class CreateBranchUseCase {

    private final FranchiseRepository repository;

    public CreateBranchUseCase(FranchiseRepository repository) {
        this.repository = repository;
    }

    public Mono<Branch> execute(String franchiseId, String name) {
        return repository.saveBranch(franchiseId, new Branch(UUID.randomUUID().toString(), name, List.of()));
    }
}
