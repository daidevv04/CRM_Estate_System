package com.estatecrm.crm_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProductReservationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProductReservationScheduler.class);
    private final ProductService productService;

    public ProductReservationScheduler(ProductService productService) {
        this.productService = productService;
    }

    // Chay moi gio (phut 01) de kiem tra va giai phong cac can het han giu cho
    @Scheduled(cron = "0 1 * * * *")
    public void scanAndReleaseExpiredReservations() {
        int released = productService.releaseExpiredReservations();
        if (released > 0) {
            log.info("Auto-released {} expired product reservation(s)", released);
        }
    }
}
