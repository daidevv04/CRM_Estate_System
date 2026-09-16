package com.estatecrm.customer_service.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
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

    /**
     * Sort tro toi cot khong ton tai. Mac dinh Spring tra 500, nhung day la loi
     * cua nguoi goi (tham so sai) nen phai la 400.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail handleBadSort(PropertyReferenceException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Unknown sort field: " + exception.getPropertyName());
    }

    /**
     * Sort chua ky tu khong phai ten thuoc tinh (vi du "id;drop table"). Spring Data
     * chan tu truoc khi vao SQL, chi con phai doi 500 thanh 400.
     */
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    ProblemDetail handleInvalidSort(InvalidDataAccessApiUsageException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid sort expression");
    }

    /** Trung phone/email -> 409. */
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

    /** Doan message theo ten constraint/index co trong loi cua Postgres. */
    private String describeIntegrityViolation(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        if (message == null) {
            return "Data conflicts with an existing record";
        }
        // Khop theo phan ten on dinh: migration trong git va DB Supabase dang co
        // dung tien to khac nhau (uk_/uq_/ex_) nen khong the khop ten day du.
        if (message.contains("_no_overlap")) {
            return "Sales user already has an appointment in that time range";
        }
        if (message.contains("customers_email")) {
            return "Email already exists";
        }
        if (message.contains("customers_phone")) {
            return "Phone already exists";
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
