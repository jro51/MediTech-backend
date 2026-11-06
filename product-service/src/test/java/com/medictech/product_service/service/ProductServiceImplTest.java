package com.medictech.product_service.service;

import com.medictech.product_service.dto.InventoryAlertsResponse;
import com.medictech.product_service.dto.ProductRequest;
import com.medictech.product_service.dto.ProductResponse;
import com.medictech.product_service.entity.Product;
import com.medictech.product_service.exception.ProductNotFoundException;
import com.medictech.product_service.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product product(Long id, String name, int stock) {
        return Product.builder()
                .id(id)
                .name(name)
                .price(10.0)
                .stock(stock)
                .minStock(5)
                .requiresPrescription(false)
                .build();
    }

    private ProductRequest request() {
        ProductRequest request = new ProductRequest();
        request.setName("Ibuprofeno 400mg");
        request.setPrice(12.5);
        request.setStock(100);
        return request;
    }

    @Test
    void findAll_returnsMappedProducts() {
        when(productRepository.findAll()).thenReturn(List.of(product(1L, "A", 10), product(2L, "B", 3)));

        List<ProductResponse> result = productService.findAll();

        assertThat(result).extracting(ProductResponse::getName).containsExactly("A", "B");
    }

    @Test
    void findById_whenMissing_throwsNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void findById_returnsProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "A", 10)));

        assertThat(productService.findById(1L).getName()).isEqualTo("A");
    }

    @Test
    void create_appliesDefaultsWhenOptionalFieldsAreMissing() {
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        ProductResponse response = productService.create(request());

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMinStock()).isEqualTo(5);
        assertThat(response.getRequiresPrescription()).isFalse();
        assertThat(response.getExpirationDate()).isNull();
    }

    @Test
    void create_keepsProvidedOptionalFields() {
        ProductRequest request = request();
        request.setMinStock(20);
        request.setRequiresPrescription(true);
        request.setExpirationDate(LocalDate.of(2030, 1, 31));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.create(request);

        assertThat(response.getMinStock()).isEqualTo(20);
        assertThat(response.getRequiresPrescription()).isTrue();
        assertThat(response.getExpirationDate()).isEqualTo(LocalDate.of(2030, 1, 31));
    }

    @Test
    void update_changesFieldsOfExistingProduct() {
        Product existing = product(1L, "Viejo", 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProductRequest request = request();
        request.setMinStock(8);
        request.setRequiresPrescription(true);

        ProductResponse response = productService.update(1L, request);

        assertThat(response.getName()).isEqualTo("Ibuprofeno 400mg");
        assertThat(response.getStock()).isEqualTo(100);
        assertThat(response.getMinStock()).isEqualTo(8);
        assertThat(response.getRequiresPrescription()).isTrue();
    }

    @Test
    void update_keepsMinStockAndPrescriptionWhenNotProvided() {
        Product existing = product(1L, "Viejo", 10);
        existing.setMinStock(12);
        existing.setRequiresPrescription(true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.update(1L, request());

        assertThat(response.getMinStock()).isEqualTo(12);
        assertThat(response.getRequiresPrescription()).isTrue();
    }

    @Test
    void update_whenMissing_throwsNotFound() {
        when(productRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(5L, request()))
                .isInstanceOf(ProductNotFoundException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void delete_removesExistingProduct() {
        when(productRepository.existsById(1L)).thenReturn(true);

        productService.delete(1L);

        verify(productRepository).deleteById(1L);
    }

    @Test
    void delete_whenMissing_throwsNotFound() {
        when(productRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> productService.delete(1L))
                .isInstanceOf(ProductNotFoundException.class);
        verify(productRepository, never()).deleteById(any());
    }

    @Test
    void checkStock_comparesAvailableStockWithQuantity() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "A", 3)));

        assertThat(productService.checkStock(1L, 3)).isTrue();
        assertThat(productService.checkStock(1L, 4)).isFalse();
    }

    @Test
    void checkStock_whenMissing_throwsNotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.checkStock(1L, 1))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void reduceStock_subtractsQuantity() {
        Product existing = product(1L, "A", 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

        productService.reduceStock(1L, 4);

        assertThat(existing.getStock()).isEqualTo(6);
        verify(productRepository).save(existing);
    }

    @Test
    void reduceStock_withInsufficientStock_throwsAndDoesNotSave() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "A", 2)));

        assertThatThrownBy(() -> productService.reduceStock(1L, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stock insuficiente");
        verify(productRepository, never()).save(any());
    }

    @Test
    void reduceStock_whenMissing_throwsNotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.reduceStock(1L, 1))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void restoreStock_addsQuantityBack() {
        Product existing = product(1L, "A", 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

        productService.restoreStock(1L, 2);

        assertThat(existing.getStock()).isEqualTo(12);
        verify(productRepository).save(existing);
    }

    @Test
    void restoreStock_whenMissing_throwsNotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.restoreStock(1L, 1))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void findAlerts_returnsLowStockAndExpiringProducts() {
        when(productRepository.findLowStock()).thenReturn(List.of(product(1L, "Poco stock", 2)));
        when(productRepository.findByExpirationDateLessThanEqualOrderByExpirationDateAsc(any(LocalDate.class)))
                .thenReturn(List.of(product(2L, "Por vencer", 50)));

        InventoryAlertsResponse alerts = productService.findAlerts(30);

        assertThat(alerts.getDays()).isEqualTo(30);
        assertThat(alerts.getLowStock()).extracting(ProductResponse::getName).containsExactly("Poco stock");
        assertThat(alerts.getExpiringSoon()).extracting(ProductResponse::getName).containsExactly("Por vencer");
    }

    @Test
    void findAlerts_usesTodayPlusDaysAsLimit() {
        when(productRepository.findLowStock()).thenReturn(List.of());
        when(productRepository.findByExpirationDateLessThanEqualOrderByExpirationDateAsc(any(LocalDate.class)))
                .thenReturn(List.of());

        productService.findAlerts(10);

        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(productRepository).findByExpirationDateLessThanEqualOrderByExpirationDateAsc(captor.capture());
        assertThat(captor.getValue()).isEqualTo(LocalDate.now().plusDays(10));
    }

    @Test
    void findAlerts_withInvalidWindow_throwsIllegalArgument() {
        assertThatThrownBy(() -> productService.findAlerts(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> productService.findAlerts(366))
                .isInstanceOf(IllegalArgumentException.class);
        verify(productRepository, never()).findLowStock();
    }
}
