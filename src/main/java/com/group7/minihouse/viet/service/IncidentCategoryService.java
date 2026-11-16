package com.group7.minihouse.viet.service;

import com.group7.minihouse.shared.entity.IncidentCategory;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.viet.dto.EquipmentDtos.CategoryRequest;
import com.group7.minihouse.viet.dto.EquipmentDtos.CategoryResponse;
import com.group7.minihouse.viet.repository.IncidentCategoryRepository;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC113: quan ly danh muc nhom su co (INCIDENT) va nhom thiet bi (EQUIPMENT). */
@Service
public class IncidentCategoryService {

    static final Set<String> TYPES = Set.of("INCIDENT", "EQUIPMENT");
    private static final Pattern CODE = Pattern.compile("[A-Z0-9_]{2,50}");

    private final IncidentCategoryRepository repository;

    public IncidentCategoryService(IncidentCategoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> search(String type, Boolean active) {
        String t = VietSupport.enumOrNull(type, TYPES, "type");
        return repository.search(t, active).stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        if (req == null) {
            throw new BusinessException("Thieu du lieu");
        }
        String code = VietSupport.requireText(req.categoryCode(), "ma nhom").toUpperCase();
        if (!CODE.matcher(code).matches()) {
            throw new BusinessException("Ma nhom chi gom chu hoa, so, dau gach duoi (2-50 ky tu)");
        }
        if (repository.findByCategoryCode(code).isPresent()) {
            throw new BusinessException("Ma nhom \"" + code + "\" da ton tai");
        }
        String type = VietSupport.enumOrNull(req.categoryType(), TYPES, "categoryType");
        if (type == null) {
            throw new BusinessException("Thieu categoryType (INCIDENT hoac EQUIPMENT)");
        }
        IncidentCategory c = new IncidentCategory();
        c.setCategoryCode(code);
        c.setCategoryName(VietSupport.requireText(req.categoryName(), "ten nhom"));
        c.setCategoryType(type);
        c.setDescription(VietSupport.blankToNull(req.description()));
        c.setIsActive(true);
        return CategoryResponse.from(repository.save(c));
    }

    /** Khong doi ma nhom va loai (da duoc yeu cau/su co tham chieu). */
    @Transactional
    public CategoryResponse update(Integer id, CategoryRequest req) {
        IncidentCategory c = find(id);
        if (req == null) {
            throw new BusinessException("Thieu du lieu");
        }
        c.setCategoryName(VietSupport.requireText(req.categoryName(), "ten nhom"));
        c.setDescription(VietSupport.blankToNull(req.description()));
        return CategoryResponse.from(repository.save(c));
    }

    @Transactional
    public CategoryResponse setActive(Integer id, Boolean active) {
        if (active == null) {
            throw new BusinessException("Thieu active (true/false)");
        }
        IncidentCategory c = find(id);
        c.setIsActive(active);
        return CategoryResponse.from(repository.save(c));
    }

    private IncidentCategory find(Integer id) {
        return repository.findById(id).orElseThrow(() -> BusinessException.notFound("Nhom su co/thiet bi"));
    }
}
