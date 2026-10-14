package com.juan.franchise.application.usecase;

import org.springframework.stereotype.Service;

import com.juan.franchise.domain.exception.NotFoundException;
import com.juan.franchise.domain.model.Franchise;
import com.juan.franchise.domain.port.FranchiseRepository;

import reactor.core.publisher.Mono;

@Service
public class GetFranchiseUseCase {

    private final FranchiseRepository repository;

    public GetFranchiseUseCase(FranchiseRepository repository) {
        this.repository = repository;
    }

    public Mono<Franchise> execute(String franchiseId) {
        return repository.findFranchiseTree(franchiseId)
                .switchIfEmpty(Mono.error(new NotFoundException("Franchise " + franchiseId + " not found")));
    }
}
