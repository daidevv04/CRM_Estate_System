package com.estatecrm.customer_service.service;

import com.estatecrm.customer_service.dto.AppointmentResponse;
import com.estatecrm.customer_service.dto.CreateAppointmentRequest;
import com.estatecrm.customer_service.dto.UpdateAppointmentRequest;
import com.estatecrm.customer_service.entity.Appointment;
import com.estatecrm.customer_service.enums.AppointmentStatus;
import com.estatecrm.customer_service.exception.ConflictException;
import com.estatecrm.customer_service.repository.AppointmentRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final CustomerService customerService;

    public AppointmentService(
            AppointmentRepository appointmentRepository, CustomerService customerService) {
        this.appointmentRepository = appointmentRepository;
        this.customerService = customerService;
    }

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request, UUID actorId, String role) {
        requireOrdered(request.startTime(), request.endTime());
        requireFuture(request.startTime());
        var customer = customerService.requireAccess(request.customerId(), actorId, role);

        UUID salesId = customerService.isPrivileged(role) && request.salesId() != null
                ? request.salesId()
                : actorId;
        requireNoOverlap(salesId, request.startTime(), request.endTime(), AppointmentRepository.NO_APPOINTMENT);

        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setSalesId(salesId);
        appointment.setTitle(request.title());
        appointment.setStartTime(request.startTime());
        appointment.setEndTime(request.endTime());
        appointment.setColor(request.color());
        appointment.setReminderMinutes(request.reminderMinutes());
        appointment.setStatus(request.status() == null ? AppointmentStatus.PENDING : request.status());
        appointment.setCreatedBy(actorId);
        return AppointmentResponse.from(appointmentRepository.save(appointment));
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> list(
            UUID actorId,
            String role,
            UUID salesId,
            UUID customerId,
            AppointmentStatus status,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {
        UUID effectiveSales = customerService.isPrivileged(role) ? salesId : actorId;
        return appointmentRepository
                .findAll(AppointmentRepository.filter(effectiveSales, customerId, status, from, to), pageable)
                .map(AppointmentResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> listByCustomer(
            UUID customerId, UUID actorId, String role, Pageable pageable) {
        customerService.requireAccess(customerId, actorId, role);
        UUID effectiveSales = customerService.isPrivileged(role) ? null : actorId;
        return appointmentRepository
                .findAll(
                        AppointmentRepository.filter(effectiveSales, customerId, null, null, null),
                        pageable)
                .map(AppointmentResponse::from);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID appointmentId, UUID actorId, String role) {
        return AppointmentResponse.from(requireAccess(appointmentId, actorId, role));
    }

    @Transactional
    public AppointmentResponse update(
            UUID appointmentId, UpdateAppointmentRequest request, UUID actorId, String role) {
        Appointment appointment = requireAccess(appointmentId, actorId, role);

        LocalDateTime startTime = request.startTime() == null ? appointment.getStartTime() : request.startTime();
        LocalDateTime endTime = request.endTime() == null ? appointment.getEndTime() : request.endTime();
        requireOrdered(startTime, endTime);

        UUID salesId = customerService.isPrivileged(role) && request.salesId() != null
                ? request.salesId()
                : appointment.getSalesId();
        if (request.startTime() != null || request.endTime() != null) {
            requireFuture(startTime);
        }
        if (request.startTime() != null || request.endTime() != null || request.salesId() != null) {
            requireNoOverlap(salesId, startTime, endTime, appointmentId);
        }

        appointment.setSalesId(salesId);
        appointment.setStartTime(startTime);
        appointment.setEndTime(endTime);
        if (request.title() != null) {
            appointment.setTitle(request.title());
        }
        if (request.color() != null) {
            appointment.setColor(request.color());
        }
        if (request.reminderMinutes() != null) {
            appointment.setReminderMinutes(request.reminderMinutes());
        }
        if (request.status() != null) {
            appointment.setStatus(request.status());
        }
        appointment.setUpdatedBy(actorId);
        return AppointmentResponse.from(appointmentRepository.save(appointment));
    }

    @Transactional
    public void delete(UUID appointmentId, UUID actorId, String role) {
        appointmentRepository.delete(requireAccess(appointmentId, actorId, role));
    }

    private Appointment requireAccess(UUID appointmentId, UUID actorId, String role) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Appointment not found"));
        if (!customerService.isPrivileged(role) && !appointment.getSalesId().equals(actorId)) {
            throw new ResponseStatusException(FORBIDDEN, "Appointment is not on your calendar");
        }
        return appointment;
    }

    private void requireOrdered(LocalDateTime startTime, LocalDateTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new ResponseStatusException(BAD_REQUEST, "endTime must be after startTime");
        }
    }

    private void requireFuture(LocalDateTime startTime) {
        if (startTime.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(BAD_REQUEST, "startTime must be in the future");
        }
    }

    private void requireNoOverlap(
            UUID salesId, LocalDateTime startTime, LocalDateTime endTime, UUID excludeId) {
        if (appointmentRepository.countOverlapping(salesId, startTime, endTime, excludeId) > 0) {
            throw new ConflictException("Sales user already has an appointment in that time range");
        }
    }
}
