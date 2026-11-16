package com.group7.minihouse.viet.service;

import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Lay nguoi thuc hien thao tac (nhan vien) de ghi vao cac cot recorded_by / inspected_by / reviewed_by...
 * Khi app.security.enabled=true luon co actorId tu token. Khi tat bao mat (che do dev) khong co token
 * nen tam lay nguoi dung co id nho nhat de test duoc; KHONG dung cach nay khi demo that.
 */
@Component
public class ActorResolver {

    @PersistenceContext
    private EntityManager em;

    public User require(Integer actorId) {
        if (actorId != null) {
            User user = em.find(User.class, actorId);
            if (user == null) {
                throw BusinessException.notFound("Nguoi dung");
            }
            return user;
        }
        List<User> any = em.createQuery("select u from User u order by u.userId", User.class)
                .setMaxResults(1).getResultList();
        if (any.isEmpty()) {
            throw new BusinessException("Chua co nguoi dung nao trong he thong");
        }
        return any.get(0);
    }
}
