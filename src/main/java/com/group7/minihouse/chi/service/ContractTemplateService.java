package com.group7.minihouse.chi.service;

import com.group7.minihouse.chi.dto.ContractTemplateDTO;
import com.group7.minihouse.chi.repository.ContractTemplateRepository;
import com.group7.minihouse.shared.entity.ContractTemplate;
import com.group7.minihouse.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ContractTemplateService {

    private final ContractTemplateRepository templateRepository;

    public ContractTemplateService(ContractTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Transactional(readOnly = true)
    public List<ContractTemplateDTO.Response> getAll() {
        return templateRepository.findAllByOrderByTemplateIdDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ContractTemplateDTO.Response getById(Integer id) {
        ContractTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Mẫu hợp đồng"));
        return toResponse(t);
    }

    public ContractTemplateDTO.Response create(ContractTemplateDTO.Request req) {
        if (req.getTemplateName() == null || req.getTemplateName().isBlank()) {
            throw new BusinessException("Tên mẫu hợp đồng không được để trống");
        }
        if (req.getTemplateName().trim().length() > 100) {
            throw new BusinessException("Tên mẫu hợp đồng tối đa 100 ký tự");
        }
        if (req.getTermsContent() == null || req.getTermsContent().isBlank()) {
            throw new BusinessException("Nội dung điều khoản không được để trống");
        }

        ContractTemplate t = new ContractTemplate();
        t.setTemplateName(req.getTemplateName());
        t.setTermsContent(req.getTermsContent());
        t.setIsDefault(req.getIsDefault() != null ? req.getIsDefault() : false);
        if (Boolean.TRUE.equals(t.getIsDefault())) {
            clearOtherDefaults(null);
        }

        return toResponse(templateRepository.save(t));
    }

    /** Chỉ có 1 mẫu mặc định: đặt mẫu mới làm mặc định thì bỏ mặc định ở các mẫu còn lại. */
    private void clearOtherDefaults(Integer keepId) {
        for (ContractTemplate other : templateRepository.findAll()) {
            if (Boolean.TRUE.equals(other.getIsDefault()) && !other.getTemplateId().equals(keepId)) {
                other.setIsDefault(false);
            }
        }
    }

    public ContractTemplateDTO.Response update(Integer id, ContractTemplateDTO.Request req) {
        ContractTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Mẫu hợp đồng"));

        if (req.getTemplateName() != null && !req.getTemplateName().isBlank()) {
            t.setTemplateName(req.getTemplateName());
        }
        if (req.getTermsContent() != null && !req.getTermsContent().isBlank()) {
            t.setTermsContent(req.getTermsContent());
        }
        if (req.getIsDefault() != null) {
            t.setIsDefault(req.getIsDefault());
            if (req.getIsDefault()) {
                clearOtherDefaults(t.getTemplateId());
            }
        }

        return toResponse(templateRepository.save(t));
    }

    public void delete(Integer id) {
        if (!templateRepository.existsById(id)) {
            throw BusinessException.notFound("Mẫu hợp đồng");
        }
        templateRepository.deleteById(id);
    }

    private ContractTemplateDTO.Response toResponse(ContractTemplate t) {
        ContractTemplateDTO.Response r = new ContractTemplateDTO.Response();
        r.setTemplateId(t.getTemplateId());
        r.setTemplateName(t.getTemplateName());
        r.setTermsContent(t.getTermsContent());
        r.setIsDefault(t.getIsDefault());
        return r;
    }
}
