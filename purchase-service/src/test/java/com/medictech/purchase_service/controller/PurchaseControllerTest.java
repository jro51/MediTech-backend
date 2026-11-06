package com.medictech.purchase_service.controller;

import com.medictech.purchase_service.dto.PurchaseRequest;
import com.medictech.purchase_service.dto.PurchaseResponse;
import com.medictech.purchase_service.entity.OrderStatus;
import com.medictech.purchase_service.exception.GlobalExceptionHandler;
import com.medictech.purchase_service.exception.PurchaseNotFoundException;
import com.medictech.purchase_service.service.PurchaseService;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Prueba "standalone": no levanta Spring completo, solo el controller y el manejador de errores.
@ExtendWith(MockitoExtension.class)
class PurchaseControllerTest {

    @Mock
    private PurchaseService purchaseService;

    @InjectMocks
    private PurchaseController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private PurchaseResponse response(Long id, OrderStatus status) {
        return PurchaseResponse.builder().id(id).userId(7L).total(18.0).status(status).build();
    }

    // ---------- POST /purchases/buy ----------

    @Test
    void buy_usesUserIdFromGatewayHeaderInsteadOfBody() throws Exception {
        when(purchaseService.createPurchase(any())).thenReturn(response(1L, OrderStatus.RESERVED));

        mockMvc.perform(post("/purchases/buy")
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":999,\"productIds\":[1,2]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED"));

        ArgumentCaptor<PurchaseRequest> captor = ArgumentCaptor.forClass(PurchaseRequest.class);
        verify(purchaseService).createPurchase(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
    }

    @Test
    void buy_withoutHeader_keepsBodyUserId() throws Exception {
        when(purchaseService.createPurchase(any())).thenReturn(response(1L, OrderStatus.RESERVED));

        mockMvc.perform(post("/purchases/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":5,\"productIds\":[1]}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<PurchaseRequest> captor = ArgumentCaptor.forClass(PurchaseRequest.class);
        verify(purchaseService).createPurchase(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(5L);
    }

    @Test
    void buy_withEmptyProductList_returns400() throws Exception {
        mockMvc.perform(post("/purchases/buy")
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("al menos un producto")));

        verifyNoInteractions(purchaseService);
    }

    @Test
    void buy_whenServiceRejectsStock_returns400() throws Exception {
        when(purchaseService.createPurchase(any()))
                .thenThrow(new IllegalArgumentException("Stock insuficiente para: Ibuprofeno"));

        mockMvc.perform(post("/purchases/buy")
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[1]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Stock insuficiente para: Ibuprofeno"));
    }

    // ---------- GET /purchases/user/{userId} ----------

    @Test
    void findByUser_ownOrders_returns200() throws Exception {
        when(purchaseService.findByUserId(7L)).thenReturn(List.of(response(1L, OrderStatus.READY)));

        mockMvc.perform(get("/purchases/user/7").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("READY"));
    }

    @Test
    void findByUser_otherUsersOrders_returns403() throws Exception {
        mockMvc.perform(get("/purchases/user/8").header("X-User-Id", "7"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(purchaseService);
    }

    // ---------- GET /purchases (admin) ----------

    @Test
    void findAll_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/purchases").header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(purchaseService);
    }

    @Test
    void findAll_asAdmin_returnsOrders() throws Exception {
        when(purchaseService.findAll(null)).thenReturn(List.of(response(1L, OrderStatus.RESERVED)));

        mockMvc.perform(get("/purchases").header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void findAll_asAdminFilteredByStatus() throws Exception {
        when(purchaseService.findAll(OrderStatus.READY)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/purchases")
                        .param("status", "READY")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---------- PUT /purchases/{id}/status (admin) ----------

    @Test
    void updateStatus_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(put("/purchases/1/status").param("value", "READY"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(purchaseService);
    }

    @Test
    void updateStatus_asAdmin_returnsUpdatedOrder() throws Exception {
        when(purchaseService.updateStatus(1L, OrderStatus.READY)).thenReturn(response(1L, OrderStatus.READY));

        mockMvc.perform(put("/purchases/1/status")
                        .param("value", "READY")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void updateStatus_withUnknownStatus_returns400() throws Exception {
        mockMvc.perform(put("/purchases/1/status")
                        .param("value", "NOPE")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("value")));

        verify(purchaseService, never()).updateStatus(any(), any());
    }

    @Test
    void updateStatus_withInvalidTransition_returns400() throws Exception {
        when(purchaseService.updateStatus(1L, OrderStatus.READY))
                .thenThrow(new IllegalArgumentException("Transición no permitida: PICKED_UP -> READY"));

        mockMvc.perform(put("/purchases/1/status")
                        .param("value", "READY")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_whenOrderMissing_returns404() throws Exception {
        when(purchaseService.updateStatus(99L, OrderStatus.READY)).thenThrow(new PurchaseNotFoundException(99L));

        mockMvc.perform(put("/purchases/99/status")
                        .param("value", "READY")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound());
    }

    // ---------- DELETE /purchases/{id} (admin) ----------

    @Test
    void delete_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(delete("/purchases/1").header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(purchaseService);
    }

    @Test
    void delete_asAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/purchases/1").header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent());

        verify(purchaseService).deletePurchase(1L);
    }

    // ---------- errores de comunicación entre servicios ----------

    private FeignException feignError(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "/products/1",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
        return FeignException.errorStatus("findById",
                feign.Response.builder().status(status).reason("error").request(request)
                        .headers(Collections.emptyMap()).build());
    }

    @Test
    void whenProductServiceReturns404_returnsFriendlyNotFound() throws Exception {
        when(purchaseService.createPurchase(any())).thenThrow(feignError(404));

        mockMvc.perform(post("/purchases/buy")
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[1]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Uno o más productos no fueron encontrados"));
    }

    @Test
    void whenProductServiceFails_returnsCommunicationError() throws Exception {
        when(purchaseService.createPurchase(any())).thenThrow(feignError(503));

        mockMvc.perform(post("/purchases/buy")
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[1]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Error al comunicarse con el servicio de productos"));
    }

    @Test
    void unexpectedError_returns500WithGenericMessage() throws Exception {
        when(purchaseService.findByUserId(7L)).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/purchases/user/7").header("X-User-Id", "7"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Error inesperado en el servidor"));
    }
}
