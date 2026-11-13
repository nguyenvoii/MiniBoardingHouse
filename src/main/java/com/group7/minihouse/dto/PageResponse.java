package com.group7.minihouse.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Khung response phan trang chung. */
public class PageResponse<T> {

    private final List<T> items;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public PageResponse(Page<T> p) {
        this.items = p.getContent();
        this.page = p.getNumber();
        this.size = p.getSize();
        this.totalElements = p.getTotalElements();
        this.totalPages = p.getTotalPages();
    }

    public List<T> getItems() { return items; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
}
