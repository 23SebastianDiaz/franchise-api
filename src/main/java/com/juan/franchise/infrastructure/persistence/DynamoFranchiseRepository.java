package com.juan.franchise.infrastructure.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.juan.franchise.domain.exception.NotFoundException;
import com.juan.franchise.domain.model.Branch;
import com.juan.franchise.domain.model.Franchise;
import com.juan.franchise.domain.model.Product;
import com.juan.franchise.domain.port.FranchiseRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionCheck;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

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
        DeleteItemRequest request = DeleteItemRequest.builder()
                .tableName(table)
                .key(productKey(franchiseId, branchId, productId))
                .conditionExpression(EXISTS)
                .build();
        return Mono.fromFuture(() -> client.deleteItem(request))
                .onErrorMap(ConditionalCheckFailedException.class,
                        e -> productNotFound(franchiseId, branchId, productId))
                .then();
    }

    @Override
    public Mono<Product> updateStock(String franchiseId, String branchId, String productId, int stock) {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(table)
                .key(productKey(franchiseId, branchId, productId))
                .updateExpression("SET stock = :stock")
                .conditionExpression(EXISTS)
                .expressionAttributeValues(Map.of(":stock", AttributeValue.builder().n(String.valueOf(stock)).build()))
                .returnValues(ReturnValue.ALL_NEW)
                .build();
        return Mono.fromFuture(() -> client.updateItem(request))
                .map(response -> new Product(
                        productId,
                        response.attributes().get("name").s(),
                        Integer.parseInt(response.attributes().get("stock").n())))
                .onErrorMap(ConditionalCheckFailedException.class,
                        e -> productNotFound(franchiseId, branchId, productId));
    }

    @Override
    public Mono<Franchise> renameFranchise(String franchiseId, String name) {
        return rename(Map.of("PK", s(franchisePk(franchiseId)), "SK", s("METADATA")), name,
                "Franchise " + franchiseId + " not found")
                .map(attrs -> new Franchise(franchiseId, attrs.get("name").s(), List.of()));
    }

    @Override
    public Mono<Branch> renameBranch(String franchiseId, String branchId, String name) {
        return rename(Map.of("PK", s(franchisePk(franchiseId)), "SK", s("BRANCH#" + branchId)), name,
                "Branch " + branchId + " not found in franchise " + franchiseId)
                .map(attrs -> new Branch(branchId, attrs.get("name").s(), List.of()));
    }

    @Override
    public Mono<Product> renameProduct(String franchiseId, String branchId, String productId, String name) {
        return rename(productKey(franchiseId, branchId, productId), name,
                productNotFound(franchiseId, branchId, productId).getMessage())
                .map(attrs -> new Product(productId, attrs.get("name").s(),
                        Integer.parseInt(attrs.get("stock").n())));
    }

    @Override
    public Mono<Franchise> findFranchiseTree(String franchiseId) {
        QueryRequest request = QueryRequest.builder()
                .tableName(table)
                .keyConditionExpression("PK = :pk")
                .expressionAttributeValues(Map.of(":pk", s(franchisePk(franchiseId))))
                .build();
        // The paginator follows LastEvaluatedKey, so we are safe past the 1 MB page limit.
        return Flux.from(client.queryPaginator(request))
                .flatMapIterable(QueryResponse::items)
                .collectList()
                .flatMap(items -> Mono.justOrEmpty(toFranchise(franchiseId, items)));
    }

    /** Rebuilds the franchise -> branches -> products tree from the items of a single partition. */
    private static Optional<Franchise> toFranchise(String franchiseId, List<Map<String, AttributeValue>> items) {
        Optional<String> franchiseName = items.stream()
                .filter(i -> i.get("SK").s().equals("METADATA"))
                .map(i -> i.get("name").s())
                .findFirst();
        if (franchiseName.isEmpty()) {
            return Optional.empty();
        }

        Map<String, List<Product>> productsByBranch = new HashMap<>();
        for (Map<String, AttributeValue> item : items) {
            String sk = item.get("SK").s();
            int marker = sk.indexOf("#PRODUCT#");
            if (sk.startsWith("BRANCH#") && marker > 0) {
                String branchId = sk.substring("BRANCH#".length(), marker);
                String productId = sk.substring(marker + "#PRODUCT#".length());
                productsByBranch.computeIfAbsent(branchId, k -> new ArrayList<>()).add(new Product(
                        productId, item.get("name").s(), Integer.parseInt(item.get("stock").n())));
            }
        }

        List<Branch> branches = items.stream()
                .filter(i -> i.get("SK").s().startsWith("BRANCH#") && !i.get("SK").s().contains("#PRODUCT#"))
                .map(i -> {
                    String branchId = i.get("SK").s().substring("BRANCH#".length());
                    return new Branch(branchId, i.get("name").s(),
                            productsByBranch.getOrDefault(branchId, List.of()));
                })
                .toList();
        return Optional.of(new Franchise(franchiseId, franchiseName.get(), branches));
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

    /** Updates only the name of an existing item and returns its new attributes. */
    private Mono<Map<String, AttributeValue>> rename(Map<String, AttributeValue> key, String name,
                                                     String notFoundMessage) {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(table)
                .key(key)
                .updateExpression("SET #n = :name") // "name" is a DynamoDB reserved word
                .conditionExpression(EXISTS)
                .expressionAttributeNames(Map.of("#n", "name"))
                .expressionAttributeValues(Map.of(":name", s(name)))
                .returnValues(ReturnValue.ALL_NEW)
                .build();
        return Mono.fromFuture(() -> client.updateItem(request))
                .map(response -> response.attributes())
                .onErrorMap(ConditionalCheckFailedException.class, e -> new NotFoundException(notFoundMessage));
    }

    private static Map<String, AttributeValue> productKey(String franchiseId, String branchId, String productId) {
        return Map.of(
                "PK", s(franchisePk(franchiseId)),
                "SK", s("BRANCH#" + branchId + "#PRODUCT#" + productId));
    }

    private static NotFoundException productNotFound(String franchiseId, String branchId, String productId) {
        return new NotFoundException("Product " + productId + " not found in branch " + branchId
                + " of franchise " + franchiseId);
    }

    private static String franchisePk(String franchiseId) {
        return "FRANCHISE#" + franchiseId;
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
