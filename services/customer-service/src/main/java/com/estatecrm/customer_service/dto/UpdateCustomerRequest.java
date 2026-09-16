package com.estatecrm.customer_service.dto;

import com.estatecrm.customer_service.enums.CustomerStatus;
import com.estatecrm.customer_service.enums.DemandType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateCustomerRequest(
        @Size(max = 100) String fullName,
        @Pattern(regexp = "^\\+?[0-9]{8,15}$") String phone,
        @Email @Size(max = 100) String email,
        DemandType demandType,
        @Size(max = 50) String source,
        UUID ownerId,
        CustomerStatus status) {
}
