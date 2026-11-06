package com.medictech.purchase_service.repository;

import com.medictech.purchase_service.entity.OrderStatus;
import com.medictech.purchase_service.entity.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    List<Purchase> findByUserIdOrderByPurchaseDateDesc(Long userId);

    List<Purchase> findByStatusOrderByPurchaseDateDesc(OrderStatus status);
}
