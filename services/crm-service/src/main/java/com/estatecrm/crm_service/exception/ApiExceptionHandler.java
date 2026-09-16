package com.estatecrm.crm_service.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Chuyen exception thanh response loi chuan ProblemDetail (RFC 9457). */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Loi co status cu the (404 khong tim thay, 400 sai tham so...). Khong co
     * handler nay thi Spring dispatch sang /error, va /error tung bi Security
     * chan nen response thanh 401 rong, mat ca status lan message.
     */
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleResponseStatus(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }

    /** Trung ma can / ma hop dong -> 409. */
    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    /**
     * Loi rang buoc DB -> 409. Xay ra khi hai request dong thoi lot qua buoc
     * kiem tra truoc cua service. Doc ten constraint de tra message dung ngu
     * canh thay vi mot cau chung chung.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, describeIntegrityViolation(exception));
    }

    /**
     * Doan message theo ten constraint co trong loi cua Postgres.
     * Ten constraint o day la ten that trong V1__init.sql (hau to _check va
     * _non_negative bi bo qua vi cung mot cau tra loi cho ca nhom).
     */
    private String describeIntegrityViolation(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        if (message == null) {
            return "Data conflicts with an existing record";
        }
        if (message.contains("uq_products_project_code")) {
            return "Product code already exists in this project";
        }
        if (message.contains("uq_deals_contract_code")) {
            return "Contract code already exists";
        }
        if (message.contains("uq_deals_lead_id")) {
            return "Lead already has a contract";
        }
        if (message.contains("uq_contact_detail_deal_product")) {
            return "Product is already in this contract";
        }
        return "Data conflicts with an existing record";
    }

    /** @Valid that bai -> 400, kem danh sach field loi. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList());
        return problem;
    }
}
