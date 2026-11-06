package com.medictech.product_service.repository;

import com.medictech.product_service.entity.ProductBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductBatchRepository extends JpaRepository<ProductBatch, Long> {

    /**
     * Lotes con stock disponible de un producto, ordenados para FEFO.
     * Los lotes sin vencimiento quedan al final.
     */
    @Query("""
            select b
              from ProductBatch b
             where b.product.id = :productId
               and b.quantity > 0
             order by case when b.expirationDate is null then 1 else 0 end,
                      b.expirationDate asc,
                      b.receivedAt asc,
                      b.id asc
            """)
    List<ProductBatch> findAvailableForFefo(@Param("productId") Long productId);

    /**
     * Todos los lotes de un producto, incluidos los agotados.
     * Útil para consultas de inventario y auditoría.
     */
    @Query("""
            select b
              from ProductBatch b
             where b.product.id = :productId
             order by case when b.expirationDate is null then 1 else 0 end,
                      b.expirationDate asc,
                      b.receivedAt asc,
                      b.id asc
            """)
    List<ProductBatch> findByProductIdForFefo(@Param("productId") Long productId);

    /**
     * Suma de unidades disponibles en todos los lotes del producto.
     * Permite conciliar el detalle de lotes con el stock agregado del producto.
     */
    @Query("select coalesce(sum(b.quantity), 0) from ProductBatch b where b.product.id = :productId")
    long sumQuantityByProductId(@Param("productId") Long productId);

    /**
     * Elimina los lotes de un producto. Se usará al borrar un producto para no
     * dejar filas huérfanas cuando el stock pase a manejarse por lotes.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ProductBatch b where b.product.id = :productId")
    int deleteByProductId(@Param("productId") Long productId);
}
