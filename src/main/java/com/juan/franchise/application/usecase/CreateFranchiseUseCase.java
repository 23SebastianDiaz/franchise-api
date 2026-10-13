package com.juan.franchise.application.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.juan.franchise.domain.model.Franchise;
import com.juan.franchise.domain.port.FranchiseRepository;

import reactor.core.publisher.Mono;

@Service
public class CreateFranchiseUseCase {

    private final FranchiseRepository repository;

    public CreateFranchiseUseCase(FranchiseRepository repository) {
        this.repository = repository;
    }

    public Mono<Franchise> execute(String name) {
        return repository.saveFranchise(new Franchise(UUID.randomUUID().toString(), name, List.of()));
    }
}
