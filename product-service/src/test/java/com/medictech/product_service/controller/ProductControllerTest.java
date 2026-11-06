package com.medictech.product_service.controller;

import com.medictech.product_service.dto.InventoryAlertsResponse;
import com.medictech.product_service.dto.ProductResponse;
import com.medictech.product_service.exception.GlobalExceptionHandler;
import com.medictech.product_service.exception.ProductNotFoundException;
import com.medictech.product_service.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Prueba "standalone": no levanta Spring completo, solo el controller y el manejador de errores.
@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    private static final String VALID_BODY = "{\"name\":\"Ibuprofeno\",\"price\":12.5,\"stock\":10}";

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ProductResponse response(Long id, String name) {
        return ProductResponse.builder().id(id).name(name).price(12.5).stock(10).build();
    }

    @Test
    void findAll_returnsProducts() throws Exception {
        when(productService.findAll()).thenReturn(List.of(response(1L, "Ibuprofeno")));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ibuprofeno"));
    }

    @Test
    void findById_whenMissing_returns404() throws Exception {
        when(productService.findById(9L)).thenThrow(new ProductNotFoundException(9L));

        mockMvc.perform(get("/products/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void findById_returnsProduct() throws Exception {
        when(productService.findById(1L)).thenReturn(response(1L, "Ibuprofeno"));

        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void create_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productService);
    }

    @Test
    void create_asAdmin_returns201() throws Exception {
        when(productService.create(any())).thenReturn(response(1L, "Ibuprofeno"));

        mockMvc.perform(post("/products")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ibuprofeno"));
    }

    @Test
    void create_withInvalidBody_returns400WithFieldMessages() throws Exception {
        mockMvc.perform(post("/products")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"price\":-1,\"stock\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("El nombre es obligatorio")))
                .andExpect(jsonPath("$.message").value(containsString("El precio debe ser mayor a 0")))
                .andExpect(jsonPath("$.message").value(containsString("El stock no puede ser negativo")));

        verify(productService, never()).create(any());
    }

    @Test
    void update_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(put("/products/1")
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productService);
    }

    @Test
    void update_asAdmin_returns200() throws Exception {
        when(productService.update(eq(1L), any())).thenReturn(response(1L, "Ibuprofeno"));

        mockMvc.perform(put("/products/1")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void delete_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(delete("/products/1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productService);
    }

    @Test
    void delete_asAdmin_returns200() throws Exception {
        mockMvc.perform(delete("/products/1").header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(content().string("Producto eliminado correctamente"));

        verify(productService).delete(1L);
    }

    @Test
    void alerts_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/products/alerts").header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productService);
    }

    @Test
    void alerts_asAdmin_returnsLowStockAndExpiring() throws Exception {
        InventoryAlertsResponse alerts = InventoryAlertsResponse.builder()
                .days(15)
                .lowStock(List.of(response(1L, "Poco stock")))
                .expiringSoon(List.of(response(2L, "Por vencer")))
                .build();
        when(productService.findAlerts(15)).thenReturn(alerts);

        mockMvc.perform(get("/products/alerts")
                        .param("days", "15")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(15))
                .andExpect(jsonPath("$.lowStock[0].name").value("Poco stock"))
                .andExpect(jsonPath("$.expiringSoon[0].name").value("Por vencer"));
    }

    @Test
    void alerts_usesThirtyDaysByDefault() throws Exception {
        when(productService.findAlerts(30)).thenReturn(InventoryAlertsResponse.builder().days(30).build());

        mockMvc.perform(get("/products/alerts").header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(30));
    }

    @Test
    void alerts_withInvalidWindow_returns400() throws Exception {
        when(productService.findAlerts(0)).thenThrow(new IllegalArgumentException("days inválido"));

        mockMvc.perform(get("/products/alerts")
                        .param("days", "0")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unexpectedError_returns500WithGenericMessage() throws Exception {
        when(productService.findAll()).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/products"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Error inesperado en el servidor"));
    }

    @Test
    void checkStock_returnsBoolean() throws Exception {
        when(productService.checkStock(1L, 2)).thenReturn(true);

        mockMvc.perform(get("/products/1/check-stock").param("quantity", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void reduceStock_callsService() throws Exception {
        mockMvc.perform(put("/products/1/reduce-stock").param("quantity", "1"))
                .andExpect(status().isOk());

        verify(productService).reduceStock(1L, 1);
    }

    @Test
    void restoreStock_callsService() throws Exception {
        mockMvc.perform(put("/products/1/restore-stock").param("quantity", "1"))
                .andExpect(status().isOk());

        verify(productService).restoreStock(1L, 1);
    }
}
