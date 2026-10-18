package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.CommissionDtos.*;
import com.estatecrm.crm_service.entity.*;
import com.estatecrm.crm_service.enums.CommissionMilestoneStatus;
import com.estatecrm.crm_service.enums.CommissionStatus;
import com.estatecrm.crm_service.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DealCommissionService {

    private static final Logger log = LoggerFactory.getLogger(DealCommissionService.class);

    private final DealCommissionRepository commissionRepository;
    private final DealCommissionMilestoneRepository milestoneRepository;
    private final ProjectCommissionPolicyRepository policyRepository;
    private final DealPaymentRepository paymentRepository;
    private final LeadRepository leadRepository;
    private final DealRepository dealRepository;
    private final ObjectMapper objectMapper;

    public DealCommissionService(
            DealCommissionRepository commissionRepository,
            DealCommissionMilestoneRepository milestoneRepository,
            ProjectCommissionPolicyRepository policyRepository,
            DealPaymentRepository paymentRepository,
            LeadRepository leadRepository,
            DealRepository dealRepository,
            ObjectMapper objectMapper) {
        this.commissionRepository = commissionRepository;
        this.milestoneRepository = milestoneRepository;
        this.policyRepository = policyRepository;
        this.paymentRepository = paymentRepository;
        this.leadRepository = leadRepository;
        this.dealRepository = dealRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DealCommissionResponse initCommissionForDeal(Deal deal) {
        Optional<DealCommission> existing = commissionRepository.findByDealId(deal.getId());
        if (existing.isPresent()) {
            List<DealCommissionMilestone> milestones = milestoneRepository
                    .findByDealCommissionIdOrderByMilestoneNoAsc(existing.get().getId());
            return DealCommissionResponse.from(existing.get(), milestones);
        }

        UUID projectId = null;
        if (deal.getLeadId() != null) {
            projectId = leadRepository.findById(deal.getLeadId())
                    .map(Lead::getProjectId)
                    .orElse(null);
        }

        BigDecimal rate = new BigDecimal("2.50");
        BigDecimal bonus = BigDecimal.ZERO;
        List<MilestoneRuleDto> rules = defaultMilestoneRules();

        if (projectId != null) {
            Optional<ProjectCommissionPolicy> policyOpt = policyRepository.findByProjectId(projectId);
            if (policyOpt.isPresent()) {
                ProjectCommissionPolicy p = policyOpt.get();
                if (p.getCommissionRate() != null && p.getCommissionRate().compareTo(BigDecimal.ZERO) > 0) {
                    rate = p.getCommissionRate();
                }
                if (p.getDefaultBonusAmount() != null) {
                    bonus = p.getDefaultBonusAmount();
                }
                if (p.getMilestoneRules() != null && !p.getMilestoneRules().isBlank()) {
                    try {
                        List<MilestoneRuleDto> parsed = objectMapper.readValue(
                                p.getMilestoneRules(),
                                new TypeReference<List<MilestoneRuleDto>>() {}
                        );
                        if (parsed != null && !parsed.isEmpty()) {
                            rules = parsed;
                        }
                    } catch (Exception e) {
                        log.warn("Lỗi parse milestoneRules từ policy dự án, dùng mẫu mặc định: {}", e.getMessage());
                    }
                }
            }
        }

        BigDecimal contractVal = deal.getContractValue() != null ? deal.getContractValue() : BigDecimal.ZERO;
        BigDecimal commAmount = contractVal.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal totalExpected = commAmount.add(bonus);

        DealCommission commission = new DealCommission();
        commission.setDealId(deal.getId());
        commission.setSalesId(deal.getSalesId());
        commission.setContractValue(contractVal);
        commission.setCommissionRate(rate);
        commission.setCommissionAmount(commAmount);
        commission.setBonusAmount(bonus);
        commission.setTotalExpected(totalExpected);
        commission.setTotalPaid(BigDecimal.ZERO);
        commission.setStatus(CommissionStatus.PENDING);
        DealCommission saved = commissionRepository.save(commission);

        List<DealCommissionMilestone> milestoneEntities = new ArrayList<>();
        for (MilestoneRuleDto r : rules) {
            DealCommissionMilestone m = new DealCommissionMilestone();
            m.setDealCommissionId(saved.getId());
            m.setMilestoneNo(r.milestoneNo());
            m.setName(r.name());
            m.setCustomerPaidThresholdPct(r.customerPaidThresholdPct());
            m.setPayoutPercentage(r.payoutPercentage());
            BigDecimal amt = totalExpected.multiply(r.payoutPercentage())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            m.setAmountExpected(amt);
            m.setAmountPaid(BigDecimal.ZERO);
            m.setStatus(CommissionMilestoneStatus.PENDING_MILESTONE);
            milestoneEntities.add(m);
        }
        List<DealCommissionMilestone> savedMilestones = milestoneRepository.saveAll(milestoneEntities);

        syncMilestonesFromPayments(deal.getId());

        return DealCommissionResponse.from(saved, savedMilestones);
    }

    @Transactional
    public void syncMilestonesFromPayments(UUID dealId) {
        Optional<DealCommission> commOpt = commissionRepository.findByDealId(dealId);
        if (commOpt.isEmpty()) {
            return;
        }
        DealCommission commission = commOpt.get();
        if (commission.getContractValue() == null || commission.getContractValue().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal totalPaid = paymentRepository.totalPaidByDealId(dealId);
        if (totalPaid == null) {
            totalPaid = BigDecimal.ZERO;
        }

        BigDecimal paidPct = totalPaid.multiply(BigDecimal.valueOf(100))
                .divide(commission.getContractValue(), 2, RoundingMode.HALF_UP);

        List<DealCommissionMilestone> milestones = milestoneRepository
                .findByDealCommissionIdOrderByMilestoneNoAsc(commission.getId());

        boolean changed = false;
        for (DealCommissionMilestone m : milestones) {
            if (m.getStatus() == CommissionMilestoneStatus.PENDING_MILESTONE) {
                if (paidPct.compareTo(m.getCustomerPaidThresholdPct()) >= 0) {
                    m.setStatus(CommissionMilestoneStatus.ELIGIBLE);
                    changed = true;
                }
            }
        }
        if (changed) {
            milestoneRepository.saveAll(milestones);
        }
    }

    @Transactional
    public DealCommissionMilestoneResponse approveMilestone(UUID milestoneId, UUID managerId) {
        DealCommissionMilestone m = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đợt giải ngân hoa hồng"));

        if (m.getStatus() != CommissionMilestoneStatus.ELIGIBLE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ đợt hoa hồng đã đạt tỷ lệ nộp tiền (ELIGIBLE) mới có thể phê duyệt");
        }

        m.setStatus(CommissionMilestoneStatus.APPROVED);
        m.setApprovedBy(managerId);
        m.setApprovedAt(LocalDateTime.now());
        DealCommissionMilestone saved = milestoneRepository.save(m);
        return DealCommissionMilestoneResponse.from(saved);
    }

    @Transactional
    public DealCommissionMilestoneResponse payoutMilestone(UUID milestoneId, UUID actorId, String note) {
        DealCommissionMilestone m = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đợt giải ngân hoa hồng"));

        if (m.getStatus() != CommissionMilestoneStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đợt hoa hồng cần được phê duyệt (APPROVED) trước khi chi trả");
        }

        m.setStatus(CommissionMilestoneStatus.PAID);
        m.setAmountPaid(m.getAmountExpected());
        m.setPaidAt(LocalDateTime.now());
        if (note != null && !note.isBlank()) {
            m.setNote(note);
        }
        DealCommissionMilestone saved = milestoneRepository.save(m);

        // Cập nhật tổng đã chi trả của DealCommission
        DealCommission commission = commissionRepository.findById(m.getDealCommissionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản ghi hoa hồng hợp đồng"));

        List<DealCommissionMilestone> all = milestoneRepository
                .findByDealCommissionIdOrderByMilestoneNoAsc(commission.getId());
        BigDecimal totalPaid = all.stream()
                .map(item -> item.getAmountPaid() != null ? item.getAmountPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        commission.setTotalPaid(totalPaid);
        if (totalPaid.compareTo(commission.getTotalExpected()) >= 0) {
            commission.setStatus(CommissionStatus.FULLY_PAID);
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            commission.setStatus(CommissionStatus.PARTIALLY_PAID);
        }
        commissionRepository.save(commission);

        return DealCommissionMilestoneResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMyCommissions(UUID salesId) {
        List<DealCommission> list = commissionRepository.findBySalesIdOrderByCreatedAtDesc(salesId);
        return buildCommissionsPayload(list);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAllCommissions() {
        List<DealCommission> list = commissionRepository.findAllByOrderByCreatedAtDesc();
        return buildCommissionsPayload(list);
    }

    @Transactional(readOnly = true)
    public DealCommissionResponse getCommissionByDealId(UUID dealId) {
        DealCommission c = commissionRepository.findByDealId(dealId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chưa có hoa hồng cho hợp đồng này"));
        List<DealCommissionMilestone> milestones = milestoneRepository
                .findByDealCommissionIdOrderByMilestoneNoAsc(c.getId());
        return DealCommissionResponse.from(c, milestones);
    }

    @Transactional
    public ProjectCommissionPolicyResponse getOrCreateProjectPolicy(UUID projectId) {
        ProjectCommissionPolicy policy = policyRepository.findByProjectId(projectId)
                .orElseGet(() -> {
                    ProjectCommissionPolicy p = new ProjectCommissionPolicy();
                    p.setProjectId(projectId);
                    p.setCommissionRate(BigDecimal.valueOf(2.50));
                    p.setDefaultBonusAmount(BigDecimal.ZERO);
                    try {
                        p.setMilestoneRules(objectMapper.writeValueAsString(defaultMilestoneRules()));
                    } catch (Exception e) {
                        p.setMilestoneRules("[]");
                    }
                    return policyRepository.save(p);
                });
        return ProjectCommissionPolicyResponse.from(policy);
    }

    @Transactional
    public ProjectCommissionPolicyResponse updateProjectPolicy(UUID projectId, UpdateCommissionPolicyRequest request) {
        ProjectCommissionPolicy policy = policyRepository.findByProjectId(projectId)
                .orElseGet(() -> {
                    ProjectCommissionPolicy p = new ProjectCommissionPolicy();
                    p.setProjectId(projectId);
                    return p;
                });

        if (request.commissionRate() != null) {
            policy.setCommissionRate(request.commissionRate());
        }
        if (request.defaultBonusAmount() != null) {
            policy.setDefaultBonusAmount(request.defaultBonusAmount());
        }
        if (request.milestoneRules() != null) {
            policy.setMilestoneRules(request.milestoneRules());
        }
        ProjectCommissionPolicy saved = policyRepository.save(policy);
        return ProjectCommissionPolicyResponse.from(saved);
    }

    private Map<String, Object> buildCommissionsPayload(List<DealCommission> list) {
        if (list.isEmpty()) {
            return Map.of(
                    "summary", new CommissionSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                    "commissions", List.of()
            );
        }

        List<UUID> commIds = list.stream().map(DealCommission::getId).toList();
        List<DealCommissionMilestone> allMilestones = milestoneRepository.findByDealCommissionIdInOrderByMilestoneNoAsc(commIds);
        Map<UUID, List<DealCommissionMilestone>> grouped = new HashMap<>();
        for (DealCommissionMilestone m : allMilestones) {
            grouped.computeIfAbsent(m.getDealCommissionId(), k -> new ArrayList<>()).add(m);
        }

        List<DealCommissionResponse> responses = list.stream()
                .map(c -> DealCommissionResponse.from(c, grouped.get(c.getId())))
                .toList();

        BigDecimal expectedSum = BigDecimal.ZERO;
        BigDecimal paidSum = BigDecimal.ZERO;
        BigDecimal eligibleSum = BigDecimal.ZERO;
        BigDecimal pendingSum = BigDecimal.ZERO;

        for (DealCommission c : list) {
            expectedSum = expectedSum.add(c.getTotalExpected() != null ? c.getTotalExpected() : BigDecimal.ZERO);
            paidSum = paidSum.add(c.getTotalPaid() != null ? c.getTotalPaid() : BigDecimal.ZERO);
        }

        for (DealCommissionMilestone m : allMilestones) {
            if (m.getStatus() == CommissionMilestoneStatus.ELIGIBLE || m.getStatus() == CommissionMilestoneStatus.APPROVED) {
                eligibleSum = eligibleSum.add(m.getAmountExpected() != null ? m.getAmountExpected() : BigDecimal.ZERO);
            } else if (m.getStatus() == CommissionMilestoneStatus.PENDING_MILESTONE) {
                pendingSum = pendingSum.add(m.getAmountExpected() != null ? m.getAmountExpected() : BigDecimal.ZERO);
            }
        }

        return Map.of(
                "summary", new CommissionSummaryDto(expectedSum, paidSum, eligibleSum, pendingSum),
                "commissions", responses
        );
    }

    private List<MilestoneRuleDto> defaultMilestoneRules() {
        return List.of(
                new MilestoneRuleDto(1, "Đợt 1: Đủ 15% HĐMB", BigDecimal.valueOf(15.0), BigDecimal.valueOf(50.0)),
                new MilestoneRuleDto(2, "Đợt 2: Đủ 70% nhận bàn giao căn hộ", BigDecimal.valueOf(70.0), BigDecimal.valueOf(50.0))
        );
    }
}
