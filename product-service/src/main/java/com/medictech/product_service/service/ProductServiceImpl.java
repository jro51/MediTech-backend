package com.medictech.product_service.service;

import com.medictech.product_service.dto.InventoryAlertsResponse;
import com.medictech.product_service.dto.ProductRequest;
import com.medictech.product_service.dto.ProductResponse;
import com.medictech.product_service.entity.Product;
import com.medictech.product_service.exception.ProductNotFoundException;
import com.medictech.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private static final int DEFAULT_MIN_STOCK = 5;
    private static final int MAX_ALERT_DAYS = 365;

    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        log.info("Obteniendo todos los productos");
        return productRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        log.info("Buscando producto con id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        log.info("Creando producto: {}", request.getName());

        Product product = Product.builder()
                .name(request.getName())
                .price(request.getPrice())
                .stock(request.getStock())
                .description(request.getDescription())
                .imageSrc(request.getImageSrc())
                .expirationDate(request.getExpirationDate())
                .minStock(request.getMinStock() != null ? request.getMinStock() : DEFAULT_MIN_STOCK)
                .requiresPrescription(Boolean.TRUE.equals(request.getRequiresPrescription()))
                .build();

        Product saved = productRepository.save(product);
        log.info("Producto creado con id: {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        log.info("Actualizando producto con id: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setDescription(request.getDescription());
        product.setImageSrc(request.getImageSrc());
        product.setExpirationDate(request.getExpirationDate());
        if (request.getMinStock() != null) {
            product.setMinStock(request.getMinStock());
        }
        if (request.getRequiresPrescription() != null) {
            product.setRequiresPrescription(request.getRequiresPrescription());
        }

        Product updated = productRepository.save(product);
        log.info("Producto actualizado con id: {}", updated.getId());
        return toResponse(updated);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("Eliminando producto con id: {}", id);

        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
        log.info("Producto eliminado con id: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean checkStock(Long productId, Integer quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return product.getStock() >= quantity;
    }

    @Override
    @Transactional
    public void reduceStock(Long productId, Integer quantity) {
        requirePositiveQuantity(quantity);

        // UPDATE ... WHERE stock >= quantity: la comprobación y el descuento ocurren
        // en una sola operación atómica, así dos reservas concurrentes no pueden
        // leer el mismo stock y dejarlo en negativo.
        int updated = productRepository.decreaseStock(productId, quantity);
        if (updated == 0) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ProductNotFoundException(productId));
            throw new IllegalArgumentException(
                    "Stock insuficiente para el producto: " + product.getName()
            );
        }

        log.info("Stock reducido en {} unidades para producto id: {}", quantity, productId);
    }

    @Override
    @Transactional
    public void restoreStock(Long productId, Integer quantity) {
        requirePositiveQuantity(quantity);

        int updated = productRepository.increaseStock(productId, quantity);
        if (updated == 0) {
            throw new ProductNotFoundException(productId);
        }

        log.info("Stock restaurado en {} unidades para producto id: {}", quantity, productId);
    }

    private void requirePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryAlertsResponse findAlerts(int days) {
        if (days < 1 || days > MAX_ALERT_DAYS) {
            throw new IllegalArgumentException(
                    "El parámetro days debe estar entre 1 y " + MAX_ALERT_DAYS
            );
        }

        LocalDate limit = LocalDate.now().plusDays(days);

        List<ProductResponse> lowStock = productRepository.findLowStock()
                .stream()
                .map(this::toResponse)
                .toList();

        List<ProductResponse> expiringSoon = productRepository
                .findByExpirationDateLessThanEqualOrderByExpirationDateAsc(limit)
                .stream()
                .map(this::toResponse)
                .toList();

        log.info("Alertas de inventario: {} con stock bajo, {} vencidos o por vencer",
                lowStock.size(), expiringSoon.size());

        return InventoryAlertsResponse.builder()
                .days(days)
                .lowStock(lowStock)
                .expiringSoon(expiringSoon)
                .build();
    }

    // Metodo privado que convierte entidad a DTO de respuesta
    // Patrón Mapper manual — evita dependencias externas como MapStruct
    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .stock(product.getStock())
                .description(product.getDescription())
                .imageSrc(product.getImageSrc())
                .expirationDate(product.getExpirationDate())
                .minStock(product.getMinStock())
                .requiresPrescription(product.getRequiresPrescription())
                .build();
    }
}
