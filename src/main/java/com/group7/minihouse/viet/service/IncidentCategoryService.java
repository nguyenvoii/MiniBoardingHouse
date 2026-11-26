package com.group7.minihouse.viet.service;

import com.group7.minihouse.shared.entity.IncidentCategory;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.viet.dto.EquipmentDtos.CategoryRequest;
import com.group7.minihouse.viet.dto.EquipmentDtos.CategoryResponse;
import com.group7.minihouse.viet.repository.IncidentCategoryRepository;
import java.util.List;
import java.util.Locale;
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
        String code = VietSupport.requireText(req.categoryCode(), "ma nhom").toUpperCase(Locale.ROOT);
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
        c.setCategoryType(type);
        applyDescription(c, req);
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
        String code = VietSupport.blankToNull(req.categoryCode());
        String type = VietSupport.blankToNull(req.categoryType());
        if ((code != null && !c.getCategoryCode().equalsIgnoreCase(code))
                || (type != null && !c.getCategoryType().equalsIgnoreCase(type))) {
            throw new BusinessException("Khong duoc doi ma hoac loai nhom");
        }
        applyDescription(c, req);
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

    private static void applyDescription(IncidentCategory category, CategoryRequest request) {
        String name = VietSupport.requireText(request.categoryName(), "ten nhom");
        String description = VietSupport.blankToNull(request.description());
        if (name.length() > 100 || (description != null && description.length() > 255)) {
            throw new BusinessException("Ten nhom toi da 100 ky tu, mo ta toi da 255 ky tu");
        }
        category.setCategoryName(name);
        category.setDescription(description);
    }

    private IncidentCategory find(Integer id) {
        return repository.findById(id).orElseThrow(() -> BusinessException.notFound("Nhom su co/thiet bi"));
    }
}
