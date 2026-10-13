package com.juan.franchise.domain.model;

import java.util.List;

public record Franchise(String id, String name, List<Branch> branches) {
}
