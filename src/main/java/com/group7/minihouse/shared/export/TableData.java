package com.group7.minihouse.shared.export;

import java.util.List;

/**
 * Bang du lieu de xuat: tieu de, ten cot, cac dong (moi dong dung bang so cot).
 * O co the la String, Number, LocalDate/LocalDateTime hoac null.
 */
public record TableData(String title, List<String> headers, List<List<Object>> rows) {

    public TableData {
        for (List<Object> row : rows) {
            if (row.size() != headers.size()) {
                throw new IllegalArgumentException("Moi dong phai co dung " + headers.size() + " o");
            }
        }
    }
}
