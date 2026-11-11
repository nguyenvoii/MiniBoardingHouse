package com.group7.minihouse.entity;

import jakarta.persistence.*;

/** Bang `contract_templates`. */
@Entity
@Table(name = "contract_templates")
public class ContractTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id")
    private Integer templateId;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Column(name = "terms_content", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String termsContent;

    @Column(name = "is_default")
    private Boolean isDefault;

    public ContractTemplate() {}

    public Integer getTemplateId() { return templateId; }
    public void setTemplateId(Integer templateId) { this.templateId = templateId; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public String getTermsContent() { return termsContent; }
    public void setTermsContent(String termsContent) { this.termsContent = termsContent; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

}
