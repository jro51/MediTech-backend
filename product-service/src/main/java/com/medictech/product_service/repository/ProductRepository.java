package com.medictech.product_service.repository;

import com.medictech.product_service.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Buscar productos con stock disponible
    List<Product> findByStockGreaterThan(Integer stock);

    // Buscar por nombre
    List<Product> findByNameContainingIgnoreCase(String name);

    // Productos cuyo stock llegó (o bajó de) su mínimo
    @Query("select p from Product p where p.stock <= p.minStock order by p.stock asc")
    List<Product> findLowStock();

    // Productos vencidos o que vencen hasta la fecha límite (los sin fecha no entran)
    List<Product> findByExpirationDateLessThanEqualOrderByExpirationDateAsc(LocalDate limit);

    // Descuento atómico: solo actualiza si hay stock suficiente. Evita sobreventa
    // cuando dos reservas llegan al mismo tiempo.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Product p
               set p.stock = p.stock - :quantity
             where p.id = :id
               and p.stock >= :quantity
            """)
    int decreaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    // Restauración atómica al cancelar o liberar una reserva.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :quantity where p.id = :id")
    int increaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
