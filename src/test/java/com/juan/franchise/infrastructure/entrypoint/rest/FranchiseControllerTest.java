package com.juan.franchise.infrastructure.entrypoint.rest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.juan.franchise.application.usecase.CreateBranchUseCase;
import com.juan.franchise.application.usecase.CreateFranchiseUseCase;
import com.juan.franchise.application.usecase.CreateProductUseCase;
import com.juan.franchise.application.usecase.DeleteProductUseCase;
import com.juan.franchise.application.usecase.GetTopStockProductsUseCase;
import com.juan.franchise.application.usecase.UpdateStockUseCase;
import com.juan.franchise.domain.exception.NotFoundException;
import com.juan.franchise.domain.model.Franchise;
import com.juan.franchise.domain.model.TopStockProduct;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(FranchiseController.class)
class FranchiseControllerTest {

    @Autowired WebTestClient client;

    @MockitoBean CreateFranchiseUseCase createFranchise;
    @MockitoBean CreateBranchUseCase createBranch;
    @MockitoBean CreateProductUseCase createProduct;
    @MockitoBean DeleteProductUseCase deleteProduct;
    @MockitoBean UpdateStockUseCase updateStock;
    @MockitoBean GetTopStockProductsUseCase getTopStockProducts;

    @Test
    void createFranchiseReturns201() {
        when(createFranchise.execute("Burger Co")).thenReturn(Mono.just(new Franchise("f1", "Burger Co", List.of())));

        client.post().uri("/api/v1/franchises").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Burger Co\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.id").isEqualTo("f1").jsonPath("$.name").isEqualTo("Burger Co");
    }

    @Test
    void blankNameReturns400() {
        client.post().uri("/api/v1/franchises").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"  \"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.status").isEqualTo(400).jsonPath("$.error").isEqualTo("Bad Request");
    }

    @Test
    void nameLongerThan100CharsReturns400() {
        client.post().uri("/api/v1/franchises").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"" + "a".repeat(101) + "\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void negativeStockReturns400() {
        client.post().uri("/api/v1/franchises/f1/branches/b1/products").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Soda\",\"stock\":-1}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void malformedJsonReturns400() {
        client.post().uri("/api/v1/franchises").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{not json")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void missingFranchiseReturns404() {
        when(createBranch.execute(anyString(), anyString()))
                .thenReturn(Mono.error(new NotFoundException("Franchise nope not found")));

        client.post().uri("/api/v1/franchises/nope/branches").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Centro\"}")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.message").isEqualTo("Franchise nope not found");
    }

    @Test
    void deleteProductReturns204() {
        when(deleteProduct.execute("f1", "b1", "p1")).thenReturn(Mono.empty());

        client.delete().uri("/api/v1/franchises/f1/branches/b1/products/p1")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void topStockReturnsList() {
        when(getTopStockProducts.execute("f1")).thenReturn(
                Flux.just(new TopStockProduct("b1", "Centro", "p1", "Fries", 60)));

        client.get().uri("/api/v1/franchises/f1/top-stock-products")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].branchName").isEqualTo("Centro")
                .jsonPath("$[0].productName").isEqualTo("Fries")
                .jsonPath("$[0].stock").isEqualTo(60);
    }
}
