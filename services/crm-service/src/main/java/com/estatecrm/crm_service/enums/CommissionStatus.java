package com.estatecrm.crm_service.enums;

public enum CommissionStatus {
    PENDING,
    PARTIALLY_PAID,
    FULLY_PAID,
    CANCELLED,
    // Legacy persisted state; preserve it until these commissions are reconciled.
    APPROVED
}
