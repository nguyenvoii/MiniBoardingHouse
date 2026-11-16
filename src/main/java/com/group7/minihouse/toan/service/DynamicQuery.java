package com.group7.minihouse.toan.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.persistence.EntityManager;

/**
 * Dung JPQL dong: chi them dieu kien khi tham so khac null.
 * Tranh mau ":p is null or ..." de khong bi loi kieu tham so null tren SQL Server.
 */
final class DynamicQuery {

    private final StringBuilder jpql;
    private final Map<String, Object> params = new LinkedHashMap<>();
    private String orderBy = "";

    /** @param base vi du "select i from Invoice i where 1 = 1" */
    DynamicQuery(String base) {
        this.jpql = new StringBuilder(base);
    }

    /** Them " and clause" neu value != null. clause dung tham so ten :name. */
    DynamicQuery and(String clause, String name, Object value) {
        if (value != null) {
            jpql.append(" and ").append(clause);
            params.put(name, value);
        }
        return this;
    }

    /** Dieu kien luon ap dung. */
    DynamicQuery always(String clause, String name, Object value) {
        jpql.append(" and ").append(clause);
        params.put(name, value);
        return this;
    }

    DynamicQuery orderBy(String order) {
        this.orderBy = " order by " + order;
        return this;
    }

    <T> List<T> list(EntityManager em, Class<T> type) {
        var q = em.createQuery(jpql + orderBy, type);
        params.forEach(q::setParameter);
        return q.getResultList();
    }
}
