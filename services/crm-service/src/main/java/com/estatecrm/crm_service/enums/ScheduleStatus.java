package com.estatecrm.crm_service.enums;

public enum ScheduleStatus {
    PAID,       // Đã thu đủ 100%
    OVERDUE,    // Quá hạn thanh toán (dueDate < today && amountRemaining > 0)
    DUE_SOON,   // Sắp đến hạn (dueDate <= today + 7 && amountRemaining > 0)
    PENDING     // Chưa đến hạn
}
