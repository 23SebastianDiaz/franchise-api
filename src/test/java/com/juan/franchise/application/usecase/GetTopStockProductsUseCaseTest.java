package com.juan.franchise.application.usecase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.juan.franchise.domain.exception.NotFoundException;
import com.juan.franchise.domain.model.Branch;
import com.juan.franchise.domain.model.Franchise;
import com.juan.franchise.domain.model.Product;
import com.juan.franchise.domain.model.TopStockProduct;
import com.juan.franchise.domain.port.FranchiseRepository;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GetTopStockProductsUseCaseTest {

    private final FranchiseRepository repository = mock(FranchiseRepository.class);
    private final GetTopStockProductsUseCase useCase = new GetTopStockProductsUseCase(repository);

    private void givenFranchise(Branch... branches) {
        when(repository.findFranchiseTree("f1"))
                .thenReturn(Mono.just(new Franchise("f1", "Franchise", List.of(branches))));
    }

    @Test
    void returnsTheTopProductOfEachBranch() {
        givenFranchise(
                new Branch("b1", "Centro", List.of(new Product("p1", "Burger", 10), new Product("p2", "Fries", 30))),
                new Branch("b2", "Norte", List.of(new Product("p3", "Soda", 5))));

        StepVerifier.create(useCase.execute("f1"))
                .expectNext(new TopStockProduct("b1", "Centro", "p2", "Fries", 30))
                .expectNext(new TopStockProduct("b2", "Norte", "p3", "Soda", 5))
                .verifyComplete();
    }

    @Test
    void onTieTheAlphabeticallySmallerNameWins() {
        givenFranchise(new Branch("b1", "Centro",
                List.of(new Product("p1", "Zeta", 20), new Product("p2", "Alpha", 20))));

        StepVerifier.create(useCase.execute("f1"))
                .expectNext(new TopStockProduct("b1", "Centro", "p2", "Alpha", 20))
                .verifyComplete();
    }

    @Test
    void branchesWithoutProductsAreOmitted() {
        givenFranchise(
                new Branch("b1", "Centro", List.of()),
                new Branch("b2", "Norte", List.of(new Product("p1", "Soda", 0))));

        StepVerifier.create(useCase.execute("f1"))
                .expectNext(new TopStockProduct("b2", "Norte", "p1", "Soda", 0))
                .verifyComplete();
    }

    @Test
    void franchiseWithoutBranchesReturnsEmpty() {
        givenFranchise();

        StepVerifier.create(useCase.execute("f1")).verifyComplete();
    }

    @Test
    void missingFranchiseFailsWithNotFound() {
        when(repository.findFranchiseTree("nope")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("nope"))
                .expectError(NotFoundException.class)
                .verify();
    }
}
