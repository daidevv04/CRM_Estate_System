package com.estatecrm.customer_service.dto;

import com.estatecrm.customer_service.enums.EmailCareStatus;

/**
 * PATCH trang thai email. Dat status = OPENED se tu dien openedAt neu chua co.
 * Dat sentAt rieng cho truong hop gui that bai roi gui lai.
 */
public record UpdateEmailCareRequest(
        EmailCareStatus status,
        java.time.LocalDateTime sentAt) {
}
