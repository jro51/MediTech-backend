package com.medictech.product_service.controller;

import com.medictech.product_service.dto.ProductRequest;
import com.medictech.product_service.dto.ProductResponse;
import com.medictech.product_service.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Productos", description = "Catálogo, stock y alertas de inventario de la botica")
public class ProductController {

    private static final String ADMIN = "ADMIN";
    private static final String ROLE_HEADER_DOC =
            "Rol del usuario. Lo inyecta el API Gateway a partir del JWT; "
                    + "si abres este Swagger directamente escribe ADMIN para probar.";

    private final ProductService productService;

    @Operation(summary = "Listar productos")
    @GetMapping
    public ResponseEntity<List<ProductResponse>> findAll() {
        return ResponseEntity.ok(productService.findAll());
    }

    @Operation(
            summary = "Alertas de inventario (solo ADMIN)",
            description = "Devuelve los productos con stock bajo (stock <= stock mínimo) y los "
                    + "vencidos o que vencen en los próximos N días."
    )
    @GetMapping("/alerts")
    public ResponseEntity<?> alerts(
            @RequestParam(defaultValue = "30") int days,
            @Parameter(description = ROLE_HEADER_DOC)
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (!ADMIN.equals(userRole)) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body("No tienes permisos para ver las alertas de inventario");
        }
        return ResponseEntity.ok(productService.findAlerts(days));
    }

    @Operation(summary = "Ver un producto por id")
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @Operation(summary = "Crear producto (solo ADMIN)")
    @PostMapping
    public ResponseEntity<?> create(
            @Valid @RequestBody ProductRequest request,
            // El gateway ya validó el JWT e inyectó este header
            // Ya no dependemos del frontend para saber el rol
            @Parameter(description = ROLE_HEADER_DOC)
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (!ADMIN.equals(userRole)) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body("No tienes permisos para crear productos");
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(productService.create(request));
    }

    @Operation(summary = "Actualizar producto (solo ADMIN)")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request,
            @Parameter(description = ROLE_HEADER_DOC)
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (!ADMIN.equals(userRole)) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body("No tienes permisos para editar productos");
        }

        return ResponseEntity.ok(productService.update(id, request));
    }

    @Operation(summary = "Eliminar producto (solo ADMIN)")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable Long id,
            @Parameter(description = ROLE_HEADER_DOC)
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (!ADMIN.equals(userRole)) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body("No tienes permisos para eliminar productos");
        }

        productService.delete(id);
        return ResponseEntity.ok("Producto eliminado correctamente");
    }

    // ---- Endpoints internos: el API Gateway los bloquea hacia el exterior ----

    @Operation(summary = "[Interno] Verificar stock", description = "Solo lo llama purchase-service.")
    @GetMapping("/{id}/check-stock")
    public ResponseEntity<Boolean> checkStock(
            @PathVariable Long id,
            @RequestParam Integer quantity) {
        return ResponseEntity.ok(productService.checkStock(id, quantity));
    }

    @Operation(summary = "[Interno] Reducir stock", description = "Solo lo llama purchase-service al reservar.")
    @PutMapping("/{id}/reduce-stock")
    public ResponseEntity<Void> reduceStock(
            @PathVariable Long id,
            @RequestParam Integer quantity) {
        productService.reduceStock(id, quantity);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "[Interno] Restaurar stock", description = "Solo lo llama purchase-service al cancelar.")
    @PutMapping("/{id}/restore-stock")
    public ResponseEntity<Void> restoreStock(
            @PathVariable Long id,
            @RequestParam Integer quantity) {
        productService.restoreStock(id, quantity);
        return ResponseEntity.ok().build();
    }
}
