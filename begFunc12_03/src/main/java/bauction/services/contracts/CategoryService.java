package bauction.services.contracts;

import bauction.domain.models.serviceModels.CategoryServiceModel;

import java.util.List;

public interface CategoryService {
    CategoryServiceModel addCategory(CategoryServiceModel categoryServiceModel);

    List<CategoryServiceModel> findAllCategories();

    CategoryServiceModel findByName(String name);
}
