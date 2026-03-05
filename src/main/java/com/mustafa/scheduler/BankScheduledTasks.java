package com.mustafa.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j // 🚀 LOGGER GÜCÜ (Konsola renkli ve detaylı log basmak için)
@Component
@RequiredArgsConstructor
public class BankScheduledTasks {

    // İleride buraya private final CompanyEmployeeService vs. enjekte edeceğiz.

    /**
     * CRON MANTIĞI: Saniye | Dakika | Saat | Gün | Ay | Haftanın Günü
     * Örnek: "0 22 17 * * *" -> Her gün saat 17:22:00'da çalışır.
     * * ⚠️ DİKKAT: Aşağıdaki '22' ve '17' rakamlarını şu anki saatinden 1-2 dakika sonrasına ayarla!
     */
    @Scheduled(cron = "0 24 17 * * *")
    public void testScheduledTask() {
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        // System.out.println yerine kurumsal standart olan log.info kullanıyoruz
        log.info("=====================================================");
        log.info("🚀 [OTOMASYON TETİKLENDİ] Saat: {}", currentTime);
        log.info("🛡️ Arka plan taraması yapılıyor...");
        log.info("💸 İleride buraya otomatik maaş ödemeleri, günlük kur güncellemeleri gelecek!");
        log.info("=====================================================");
    }
}