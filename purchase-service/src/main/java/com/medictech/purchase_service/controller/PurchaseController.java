package com.medictech.purchase_service.controller;

import com.medictech.purchase_service.dto.PurchaseRequest;
import com.medictech.purchase_service.dto.PurchaseResponse;
import com.medictech.purchase_service.entity.OrderStatus;
import com.medictech.purchase_service.exception.ForbiddenOperationException;
import com.medictech.purchase_service.service.PurchaseService;
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
@RequestMapping("/purchases")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Pedidos", description = "Reservas con recojo en tienda y gestión de su estado")
public class PurchaseController {

    private static final String ADMIN = "ADMIN";
    private static final String USER_ID_DOC =
            "Id del usuario. Lo inyecta el API Gateway a partir del JWT; "
                    + "si abres este Swagger directamente escribe un número para probar.";
    private static final String ROLE_DOC =
            "Rol del usuario. Lo inyecta el API Gateway a partir del JWT; "
                    + "si abres este Swagger directamente escribe ADMIN para probar.";

    private final PurchaseService purchaseService;

    @Operation(summary = "Reservar productos", description = "Crea un pedido en estado RESERVED y descuenta el stock.")
    @PostMapping("/buy")
    public ResponseEntity<PurchaseResponse> createPurchase(
            @Valid @RequestBody PurchaseRequest request,
            // El gateway inyecta estos headers desde el JWT
            @Parameter(description = USER_ID_DOC)
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        // Usamos el userId del JWT — más seguro que confiar en el body
        if (userId != null) {
            request.setUserId(Long.parseLong(userId));
        }

        log.info("Solicitud de reserva recibida para usuario: {}", request.getUserId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(purchaseService.createPurchase(request));
    }

    @Operation(summary = "Historial de pedidos de un usuario")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PurchaseResponse>> findByUserId(
            @PathVariable Long userId,
            @Parameter(description = USER_ID_DOC)
            @RequestHeader(value = "X-User-Id", required = false) String tokenUserId) {

        // Verificamos que el usuario solo vea sus propias compras
        if (tokenUserId != null && !tokenUserId.equals(String.valueOf(userId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(purchaseService.findByUserId(userId));
    }

    @Operation(summary = "Listar todos los pedidos (solo ADMIN)",
            description = "Opcionalmente filtra por estado: RESERVED, READY, PICKED_UP o CANCELLED.")
    @GetMapping
    public ResponseEntity<List<PurchaseResponse>> findAll(
            @RequestParam(required = false) OrderStatus status,
            @Parameter(description = ROLE_DOC)
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        requireAdmin(userRole);
        return ResponseEntity.ok(purchaseService.findAll(status));
    }

    @Operation(summary = "Cambiar el estado de un pedido (solo ADMIN)",
            description = "Transiciones válidas: RESERVED→READY, READY→PICKED_UP y cancelar antes de recoger.")
    @PutMapping("/{id}/status")
    public ResponseEntity<PurchaseResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam("value") OrderStatus value,
            @Parameter(description = ROLE_DOC)
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        requireAdmin(userRole);
        return ResponseEntity.ok(purchaseService.updateStatus(id, value));
    }

    @Operation(summary = "Eliminar un pedido (solo ADMIN)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePurchase(
            @PathVariable Long id,
            @Parameter(description = ROLE_DOC)
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        requireAdmin(userRole);
        purchaseService.deletePurchase(id);
        return ResponseEntity.noContent().build();
    }

    private void requireAdmin(String userRole) {
        if (!ADMIN.equals(userRole)) {
            throw new ForbiddenOperationException("No tienes permisos para esta operación");
        }
    }
}
