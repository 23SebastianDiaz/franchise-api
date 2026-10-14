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
import com.juan.franchise.application.usecase.GetFranchiseUseCase;
import com.juan.franchise.application.usecase.GetTopStockProductsUseCase;
import com.juan.franchise.application.usecase.ListFranchisesUseCase;
import com.juan.franchise.application.usecase.RenameBranchUseCase;
import com.juan.franchise.application.usecase.RenameFranchiseUseCase;
import com.juan.franchise.application.usecase.RenameProductUseCase;
import com.juan.franchise.application.usecase.UpdateStockUseCase;
import com.juan.franchise.infrastructure.entrypoint.rest.dto.CreateProductRequest;
import com.juan.franchise.infrastructure.entrypoint.rest.dto.FranchiseDetailResponse;
import com.juan.franchise.infrastructure.entrypoint.rest.dto.IdNameResponse;
import com.juan.franchise.infrastructure.entrypoint.rest.dto.NameRequest;
import com.juan.franchise.infrastructure.entrypoint.rest.dto.ProductResponse;
import com.juan.franchise.infrastructure.entrypoint.rest.dto.TopStockProductResponse;
import com.juan.franchise.infrastructure.entrypoint.rest.dto.UpdateStockRequest;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Tag(name = "Franchises")
@RestController
@RequestMapping("/api/v1/franchises")
public class FranchiseController {

    private final CreateFranchiseUseCase createFranchise;
    private final CreateBranchUseCase createBranch;
    private final CreateProductUseCase createProduct;
    private final DeleteProductUseCase deleteProduct;
    private final UpdateStockUseCase updateStock;
    private final GetTopStockProductsUseCase getTopStockProducts;
    private final RenameFranchiseUseCase renameFranchise;
    private final RenameBranchUseCase renameBranch;
    private final RenameProductUseCase renameProduct;
    private final GetFranchiseUseCase getFranchise;
    private final ListFranchisesUseCase listFranchises;

    public FranchiseController(CreateFranchiseUseCase createFranchise,
                               CreateBranchUseCase createBranch,
                               CreateProductUseCase createProduct,
                               DeleteProductUseCase deleteProduct,
                               UpdateStockUseCase updateStock,
                               GetTopStockProductsUseCase getTopStockProducts,
                               RenameFranchiseUseCase renameFranchise,
                               RenameBranchUseCase renameBranch,
                               RenameProductUseCase renameProduct,
                               GetFranchiseUseCase getFranchise,
                               ListFranchisesUseCase listFranchises) {
        this.createFranchise = createFranchise;
        this.createBranch = createBranch;
        this.createProduct = createProduct;
        this.deleteProduct = deleteProduct;
        this.updateStock = updateStock;
        this.getTopStockProducts = getTopStockProducts;
        this.renameFranchise = renameFranchise;
        this.renameBranch = renameBranch;
        this.renameProduct = renameProduct;
        this.getFranchise = getFranchise;
        this.listFranchises = listFranchises;
    }

    @GetMapping
    public Flux<FranchiseDetailResponse> listFranchises() {
        return listFranchises.execute().map(FranchiseDetailResponse::from);
    }

    @GetMapping("/{franchiseId}")
    public Mono<FranchiseDetailResponse> getFranchise(@PathVariable String franchiseId) {
        return getFranchise.execute(franchiseId).map(FranchiseDetailResponse::from);
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

    @PatchMapping("/{franchiseId}/name")
    public Mono<IdNameResponse> renameFranchise(@PathVariable String franchiseId,
                                                @Valid @RequestBody NameRequest request) {
        return renameFranchise.execute(franchiseId, request.name())
                .map(f -> new IdNameResponse(f.id(), f.name()));
    }

    @PatchMapping("/{franchiseId}/branches/{branchId}/name")
    public Mono<IdNameResponse> renameBranch(@PathVariable String franchiseId,
                                             @PathVariable String branchId,
                                             @Valid @RequestBody NameRequest request) {
        return renameBranch.execute(franchiseId, branchId, request.name())
                .map(b -> new IdNameResponse(b.id(), b.name()));
    }

    @PatchMapping("/{franchiseId}/branches/{branchId}/products/{productId}/name")
    public Mono<ProductResponse> renameProduct(@PathVariable String franchiseId,
                                               @PathVariable String branchId,
                                               @PathVariable String productId,
                                               @Valid @RequestBody NameRequest request) {
        return renameProduct.execute(franchiseId, branchId, productId, request.name())
                .map(p -> new ProductResponse(p.id(), p.name(), p.stock()));
    }
}
