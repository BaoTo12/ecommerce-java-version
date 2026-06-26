package com.ecommerce.domain.catalog.adapter.out.persistence;

import com.ecommerce.domain.catalog.domain.model.Product;
import com.ecommerce.domain.catalog.domain.ports.out.ProductRepositoryPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class ProductPersistenceAdapter implements ProductRepositoryPort {

    private final SpringDataProductRepository repository;

    public ProductPersistenceAdapter(SpringDataProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public Page<Product> findByIsActiveTrue(Pageable pageable) {
        return repository.findByIsActiveTrue(pageable).map(ProductPersistenceMapper::toDomain);
    }

    @Override
    public long countByCategory(String category) {
        return repository.countByCategory(category);
    }

    @Override
    public Page<Product> searchActive(String keyword, String category, Pageable pageable) {
        return repository.searchActive(keyword, category, pageable).map(ProductPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Product> findBySkuAndIsActiveTrue(String sku) {
        return repository.findBySkuAndIsActiveTrue(sku).map(ProductPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return repository.findById(id).map(ProductPersistenceMapper::toDomain);
    }

    @Override
    public List<Product> findAllById(List<UUID> ids) {
        return repository.findAllById(ids).stream()
                .map(ProductPersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Product save(Product product) {
        ProductJpaEntity jpa = ProductPersistenceMapper.toJpa(product);
        ProductJpaEntity saved = repository.save(jpa);
        return ProductPersistenceMapper.toDomain(saved);
    }
}
