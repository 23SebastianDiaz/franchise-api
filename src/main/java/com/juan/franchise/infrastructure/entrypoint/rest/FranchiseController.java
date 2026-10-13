package com.juan.franchise.infrastructure.entrypoint.rest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.juan.franchise.application.usecase.CreateBranchUseCase;
import com.juan.franchise.application.usecase.CreateFranchiseUseCase;
import com.juan.franchise.application.usecase.CreateProductUseCase;
import com.juan.franchise.application.usecase.DeleteProductUseCase;
import com.juan.franchise.application.usecase.GetTopStockProductsUseCase;
import com.juan.franchise.application.usecase.UpdateStockUseCase;
import com.juan.franchise.infrastructure.entrypoint.rest.dto.CreateProductRequest;
import com.juan.franchise.infrastructure.entrypoint.rest.dto.IdNameResponse;
import com.juan.franchise.infrastructure.entrypoint.rest.dto.NameRequest;
import com.juan.franchise.infrastructure.entrypoint.rest.dto.ProductResponse;
import com.juan.franchise.infrastructure.entrypoint.rest.dto.TopStockProductResponse;
import com.juan.franchise.infrastructure.entrypoint.rest.dto.UpdateStockRequest;

import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/franchises")
public class FranchiseController {

    private final CreateFranchiseUseCase createFranchise;
    private final CreateBranchUseCase createBranch;
    private final CreateProductUseCase createProduct;
    private final DeleteProductUseCase deleteProduct;
    private final UpdateStockUseCase updateStock;
    private final GetTopStockProductsUseCase getTopStockProducts;

    public FranchiseController(CreateFranchiseUseCase createFranchise,
                               CreateBranchUseCase createBranch,
                               CreateProductUseCase createProduct,
                               DeleteProductUseCase deleteProduct,
                               UpdateStockUseCase updateStock,
                               GetTopStockProductsUseCase getTopStockProducts) {
        this.createFranchise = createFranchise;
        this.createBranch = createBranch;
        this.createProduct = createProduct;
        this.deleteProduct = deleteProduct;
        this.updateStock = updateStock;
        this.getTopStockProducts = getTopStockProducts;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<IdNameResponse> createFranchise(@Valid @RequestBody NameRequest request) {
        return createFranchise.execute(request.name())
                .map(f -> new IdNameResponse(f.id(), f.name()));
    }

    @PostMapping("/{franchiseId}/branches")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<IdNameResponse> createBranch(@PathVariable String franchiseId,
                                             @Valid @RequestBody NameRequest request) {
        return createBranch.execute(franchiseId, request.name())
                .map(b -> new IdNameResponse(b.id(), b.name()));
    }

    @PostMapping("/{franchiseId}/branches/{branchId}/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ProductResponse> createProduct(@PathVariable String franchiseId,
                                               @PathVariable String branchId,
                                               @Valid @RequestBody CreateProductRequest request) {
        return createProduct.execute(franchiseId, branchId, request.name(), request.stock())
                .map(p -> new ProductResponse(p.id(), p.name(), p.stock()));
    }

    @DeleteMapping("/{franchiseId}/branches/{branchId}/products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteProduct(@PathVariable String franchiseId,
                                    @PathVariable String branchId,
                                    @PathVariable String productId) {
        return deleteProduct.execute(franchiseId, branchId, productId);
    }

    @PatchMapping("/{franchiseId}/branches/{branchId}/products/{productId}/stock")
    public Mono<ProductResponse> updateStock(@PathVariable String franchiseId,
                                             @PathVariable String branchId,
                                             @PathVariable String productId,
                                             @Valid @RequestBody UpdateStockRequest request) {
        return updateStock.execute(franchiseId, branchId, productId, request.stock())
                .map(p -> new ProductResponse(p.id(), p.name(), p.stock()));
    }

    @GetMapping("/{franchiseId}/top-stock-products")
    public Flux<TopStockProductResponse> topStockProducts(@PathVariable String franchiseId) {
        return getTopStockProducts.execute(franchiseId)
                .map(t -> new TopStockProductResponse(
                        t.branchId(), t.branchName(), t.productId(), t.productName(), t.stock()));
    }
}
