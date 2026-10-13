package com.juan.franchise.infrastructure.persistence;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.juan.franchise.domain.exception.NotFoundException;
import com.juan.franchise.domain.model.Branch;
import com.juan.franchise.domain.model.Franchise;
import com.juan.franchise.domain.model.Product;
import com.juan.franchise.domain.port.FranchiseRepository;

import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionCheck;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * DynamoDB adapter (single-table design). Key layout:
 * franchise -> PK FRANCHISE#f, SK METADATA
 * branch    -> PK FRANCHISE#f, SK BRANCH#b
 * product   -> PK FRANCHISE#f, SK BRANCH#b#PRODUCT#p
 */
@Repository
public class DynamoFranchiseRepository implements FranchiseRepository {

    private static final String NOT_EXISTS = "attribute_not_exists(PK)";
    private static final String EXISTS = "attribute_exists(PK)";

    private final DynamoDbAsyncClient client;
    private final String table;

    public DynamoFranchiseRepository(DynamoDbAsyncClient client, @Value("${dynamodb.table}") String table) {
        this.client = client;
        this.table = table;
    }

    @Override
    public Mono<Franchise> saveFranchise(Franchise franchise) {
        Map<String, AttributeValue> item = Map.of(
                "PK", s(franchisePk(franchise.id())),
                "SK", s("METADATA"),
                "name", s(franchise.name()));
        PutItemRequest request = PutItemRequest.builder()
                .tableName(table).item(item).conditionExpression(NOT_EXISTS).build();
        return Mono.fromFuture(() -> client.putItem(request)).thenReturn(franchise);
    }

    @Override
    public Mono<Branch> saveBranch(String franchiseId, Branch branch) {
        Map<String, AttributeValue> item = Map.of(
                "PK", s(franchisePk(franchiseId)),
                "SK", s("BRANCH#" + branch.id()),
                "name", s(branch.name()));
        return transactCreate(franchisePk(franchiseId), "METADATA", item,
                "Franchise " + franchiseId + " not found").thenReturn(branch);
    }

    @Override
    public Mono<Product> saveProduct(String franchiseId, String branchId, Product product) {
        Map<String, AttributeValue> item = Map.of(
                "PK", s(franchisePk(franchiseId)),
                "SK", s("BRANCH#" + branchId + "#PRODUCT#" + product.id()),
                "name", s(product.name()),
                "stock", AttributeValue.builder().n(String.valueOf(product.stock())).build());
        return transactCreate(franchisePk(franchiseId), "BRANCH#" + branchId, item,
                "Branch " + branchId + " not found in franchise " + franchiseId).thenReturn(product);
    }

    @Override
    public Mono<Void> deleteProduct(String franchiseId, String branchId, String productId) {
        return Mono.error(new UnsupportedOperationException("Implemented in T5"));
    }

    @Override
    public Mono<Product> updateStock(String franchiseId, String branchId, String productId, int stock) {
        return Mono.error(new UnsupportedOperationException("Implemented in T5"));
    }

    @Override
    public Mono<Franchise> findFranchiseTree(String franchiseId) {
        return Mono.error(new UnsupportedOperationException("Implemented in T6"));
    }

    /**
     * Atomic write: checks that the parent item exists and inserts the new item.
     * If the parent check fails the whole transaction is cancelled -> NotFoundException.
     */
    private Mono<Void> transactCreate(String parentPk, String parentSk,
                                      Map<String, AttributeValue> item, String notFoundMessage) {
        TransactWriteItem parentCheck = TransactWriteItem.builder()
                .conditionCheck(ConditionCheck.builder()
                        .tableName(table)
                        .key(Map.of("PK", s(parentPk), "SK", s(parentSk)))
                        .conditionExpression(EXISTS)
                        .build())
                .build();
        TransactWriteItem insert = TransactWriteItem.builder()
                .put(Put.builder().tableName(table).item(item).conditionExpression(NOT_EXISTS).build())
                .build();
        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(List.of(parentCheck, insert)).build();

        return Mono.fromFuture(() -> client.transactWriteItems(request))
                .onErrorMap(TransactionCanceledException.class, e -> new NotFoundException(notFoundMessage))
                .then();
    }

    private static String franchisePk(String franchiseId) {
        return "FRANCHISE#" + franchiseId;
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
