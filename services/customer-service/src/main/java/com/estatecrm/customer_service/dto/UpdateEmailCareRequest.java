package com.estatecrm.customer_service.dto;

import com.estatecrm.customer_service.enums.EmailCareStatus;

public record UpdateEmailCareRequest(
        EmailCareStatus status,
        java.time.LocalDateTime sentAt) {
}
