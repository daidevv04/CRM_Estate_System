package com.estatecrm.crm_service.enums;

public enum CommissionMilestoneStatus {
    PENDING_MILESTONE,  // Khách chưa đóng đủ tỷ lệ tiền đạt mốc
    ELIGIBLE,           // Khách đã đóng đủ tiền đạt mốc, đủ điều kiện duyệt chi
    APPROVED,           // Manager / Ban giám đốc đã duyệt chi hoa hồng
    PAID                // Kế toán / Sàn đã chi trả thực tế cho Sales
}
