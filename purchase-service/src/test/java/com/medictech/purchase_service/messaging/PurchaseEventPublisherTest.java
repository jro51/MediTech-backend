package com.medictech.purchase_service.messaging;

import com.medictech.purchase_service.dto.PurchaseEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PurchaseEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private PurchaseEventPublisher publisher;

    @Test
    void publishesEventToPurchaseEventsTopicUsingPurchaseIdAsKey() {
        PurchaseEvent event = PurchaseEvent.builder()
                .purchaseId(5L)
                .userId(1L)
                .status("RESERVED")
                .build();

        publisher.publishPurchaseCreated(event);

        verify(kafkaTemplate).send(eq("purchase-events"), eq("5"), eq(event));
    }
}
