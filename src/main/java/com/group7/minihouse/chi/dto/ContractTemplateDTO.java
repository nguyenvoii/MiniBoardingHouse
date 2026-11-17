package com.group7.minihouse.chi.dto;

public class ContractTemplateDTO {

    public static class Response {
        private Integer templateId;
        private String templateName;
        private String termsContent;
        private Boolean isDefault;

        public Integer getTemplateId() { return templateId; }
        public void setTemplateId(Integer templateId) { this.templateId = templateId; }
        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        public String getTermsContent() { return termsContent; }
        public void setTermsContent(String termsContent) { this.termsContent = termsContent; }
        public Boolean getIsDefault() { return isDefault; }
        public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
    }

    public static class Request {
        private String templateName;
        private String termsContent;
        private Boolean isDefault;

        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        public String getTermsContent() { return termsContent; }
        public void setTermsContent(String termsContent) { this.termsContent = termsContent; }
        public Boolean getIsDefault() { return isDefault; }
        public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
    }
}
