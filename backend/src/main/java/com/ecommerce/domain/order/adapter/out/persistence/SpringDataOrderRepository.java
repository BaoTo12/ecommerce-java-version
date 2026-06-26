package com.ecommerce.domain.order.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataOrderRepository extends JpaRepository<OrderJpaEntity, UUID> {

    @Query("SELECT o.id FROM OrderJpaEntity o WHERE o.userId = :userId ORDER BY o.createdAt DESC")
    Page<UUID> findOrderIdsByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT DISTINCT o FROM OrderJpaEntity o LEFT JOIN FETCH o.items WHERE o.id IN :ids")
    List<OrderJpaEntity> findByIdsWithItems(@Param("ids") List<UUID> ids);

    @Query("SELECT o FROM OrderJpaEntity o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<OrderJpaEntity> findByIdWithItems(@Param("id") UUID id);

    Page<OrderJpaEntity> findByUserId(@Param("userId") UUID userId, Pageable pageable);
}
