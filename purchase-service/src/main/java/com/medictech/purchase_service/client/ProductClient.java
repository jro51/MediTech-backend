package com.medictech.purchase_service.client;

import com.medictech.purchase_service.dto.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "product-service")
public interface ProductClient {

    // Llama a GET /products/{id} del product-service
    @GetMapping("/products/{id}")
    ProductResponse findById(@PathVariable Long id);

    // Llama a GET /products/{id}/check-stock
    @GetMapping("/products/{id}/check-stock")
    Boolean checkStock(@PathVariable Long id, @RequestParam Integer quantity);

    // Llama a PUT /products/{id}/reduce-stock
    @PutMapping("/products/{id}/reduce-stock")
    void reduceStock(@PathVariable Long id, @RequestParam Integer quantity);

    // Llama a PUT /products/{id}/restore-stock (al cancelar una reserva)
    @PutMapping("/products/{id}/restore-stock")
    void restoreStock(@PathVariable Long id, @RequestParam Integer quantity);
}
