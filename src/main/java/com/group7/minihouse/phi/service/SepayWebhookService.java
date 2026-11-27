package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.PaymentDtos.SepayWebhook;
import com.group7.minihouse.shared.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Nhan webhook SePay. Khong dung @Transactional o day: moi lan goi PaymentService.confirmSepayTransfer la 1 giao dich rieng,
 * loi nghiep vu (khong co chu tro, du lieu la...) duoc bat o day va tra 200 de SePay khong goi lai mai khong dut;
 * loi ha tang (mat DB...) cho no noi len thanh 500 de SePay goi lai sau.
 */
@Service
public class SepayWebhookService {

    private static final Logger log = LoggerFactory.getLogger(SepayWebhookService.class);

    private final PaymentService paymentService;

    public SepayWebhookService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public String handle(SepayWebhook w, String ip) {
        if (w == null || w.id() == null || w.transferAmount() == null || w.transferAmount().signum() <= 0) {
            return "Bo qua: thieu id hoac so tien";
        }
        if (!"in".equalsIgnoreCase(w.transferType())) {
            return "Bo qua: khong phai tien vao";
        }
        try {
            String result = paymentService.confirmSepayTransfer(w.id(), w.content(), w.transferAmount(), ip);
            log.info("SePay {} {} VND: {}", w.id(), w.transferAmount(), result);
            return result;
        } catch (BusinessException e) {
            log.warn("SePay {} {} VND bo qua: {}", w.id(), w.transferAmount(), e.getMessage());
            return "Bo qua: " + e.getMessage();
        }
    }
}
