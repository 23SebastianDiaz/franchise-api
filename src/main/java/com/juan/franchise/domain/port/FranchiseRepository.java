package com.juan.franchise.domain.port;

import com.juan.franchise.domain.model.Branch;
import com.juan.franchise.domain.model.Franchise;
import com.juan.franchise.domain.model.Product;
import reactor.core.publisher.Mono;

/**
 * Output port: what the domain needs from persistence, without knowing about DynamoDB.
 * Methods signal a missing parent/target with {@code NotFoundException}.
 */
public interface FranchiseRepository {

    Mono<Franchise> saveFranchise(Franchise franchise);

    Mono<Branch> saveBranch(String franchiseId, Branch branch);

    Mono<Product> saveProduct(String franchiseId, String branchId, Product product);

    Mono<Void> deleteProduct(String franchiseId, String branchId, String productId);

    Mono<Product> updateStock(String franchiseId, String branchId, String productId, int stock);

    /** Franchise with its branches and products; empty if the franchise does not exist. */
    Mono<Franchise> findFranchiseTree(String franchiseId);
}
