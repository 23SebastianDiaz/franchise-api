package com.juan.franchise.application.usecase;

import java.util.Comparator;

import org.springframework.stereotype.Service;

import com.juan.franchise.domain.model.Franchise;
import com.juan.franchise.domain.port.FranchiseRepository;

import reactor.core.publisher.Flux;

@Service
public class ListFranchisesUseCase {

    private final FranchiseRepository repository;

    public ListFranchisesUseCase(FranchiseRepository repository) {
        this.repository = repository;
    }

    public Flux<Franchise> execute() {
        return repository.findAllFranchises()
                .sort(Comparator.comparing(Franchise::name).thenComparing(Franchise::id));
    }
}
