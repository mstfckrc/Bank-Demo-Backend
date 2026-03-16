package com.mustafa.config; // Kendi paket adına göre düzenle

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMQPublisher {

    // Spring'in bize sunduğu o meşhur RabbitMQ telsizi
    private final RabbitTemplate rabbitTemplate;

    /**
     * Servislerin (Örn: TransactionService) gelip çağıracağı TEK metot.
     * @param message Gönderilecek olan paket (DTO, String, ID fark etmez)
     */
    public void sendNotification(Object message) {
        log.info("📢 Telsiz: Mesaj RabbitMQ postanesine fırlatılıyor... Paket: {}", message.toString());

        // Zarfı al, üzerine "bank_exchange" santralini ve "notification_routing_key" adresini yaz, fırlat!
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.BANK_EXCHANGE,
                RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                message
        );

        log.info("✅ Zarf başarıyla postaneyle teslim edildi! (Kullanıcı bekletilmiyor)");
    }
}