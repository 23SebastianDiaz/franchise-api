package com.juan.franchise.application.usecase;

import java.util.Comparator;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.juan.franchise.domain.exception.NotFoundException;
import com.juan.franchise.domain.model.Branch;
import com.juan.franchise.domain.model.Product;
import com.juan.franchise.domain.model.TopStockProduct;
import com.juan.franchise.domain.port.FranchiseRepository;

import reactor.core.publisher.Flux;

@Service
public class GetTopStockProductsUseCase {

    /** Highest stock first; on a tie the alphabetically smaller name wins (deterministic). */
    private static final Comparator<Product> TOP_FIRST =
            Comparator.comparingInt(Product::stock).reversed().thenComparing(Product::name);

    private final FranchiseRepository repository;

    public GetTopStockProductsUseCase(FranchiseRepository repository) {
        this.repository = repository;
    }

    public Flux<TopStockProduct> execute(String franchiseId) {
        return repository.findFranchiseTree(franchiseId)
                .switchIfEmpty(reactor.core.publisher.Mono.error(
                        new NotFoundException("Franchise " + franchiseId + " not found")))
                .flatMapMany(franchise -> Flux.fromIterable(franchise.branches()))
                .sort(Comparator.comparing(Branch::name).thenComparing(Branch::id))
                .concatMap(branch -> Flux.fromStream(topProductOf(branch).stream()));
    }

    /** Branches without products yield nothing, so they are omitted from the result. */
    private static Optional<TopStockProduct> topProductOf(Branch branch) {
        return branch.products().stream()
                .min(TOP_FIRST)
                .map(p -> new TopStockProduct(branch.id(), branch.name(), p.id(), p.name(), p.stock()));
    }
}
