package com.javaorm.sample.service;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.pagination.Page;
import com.javaorm.sample.dto.MallProductDto;
import com.javaorm.sample.entity.MallCategory;
import com.javaorm.sample.entity.MallProduct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductService {

    @Autowired
    private JavaOrm orm;

    @Autowired
    private CategoryService categoryService;

    /**
     * 分页查询商品（流式条件构建，自动识别 @TableLogic 过滤逻辑删除）
     */
    public Page<MallProductDto> pageProducts(String keyword, Long categoryId, Integer status, int page, int size) {
        // 构建 JavaORM 流式查询
        com.javaorm.core.query.SelectBuilder<MallProduct> builder = orm.select(MallProduct.class);

        // 动态条件
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        builder.where()
                .like(hasKeyword, MallProduct::getName, hasKeyword ? "%" + keyword.trim() + "%" : "")
                .eq(categoryId != null, MallProduct::getCategoryId, categoryId)
                .eq(status != null, MallProduct::getStatus, status);

        builder.orderBy(MallProduct::getId, false);

        // 分页查询实体
        Page<MallProduct> productPage = builder.toPage(page, size);

        // 批量关联分类名称转换为 DTO
        List<MallCategory> allCategories = categoryService.listAll();
        Map<Long, String> categoryNameMap = allCategories.stream()
                .collect(Collectors.toMap(MallCategory::getId, MallCategory::getName, (k1, k2) -> k1));

        List<MallProductDto> dtoList = new ArrayList<>();
        for (MallProduct p : productPage.getRecords()) {
            MallProductDto dto = new MallProductDto();
            dto.setId(p.getId());
            dto.setCategoryId(p.getCategoryId());
            dto.setCategoryName(categoryNameMap.getOrDefault(p.getCategoryId(), "未知分类"));
            dto.setName(p.getName());
            dto.setTitle(p.getTitle());
            dto.setPrice(p.getPrice());
            dto.setStock(p.getStock());
            dto.setSales(p.getSales());
            dto.setImage(p.getImage());
            dto.setStatus(p.getStatus());
            dto.setVersion(p.getVersion());
            dto.setCreateTime(p.getCreateTime());
            dtoList.add(dto);
        }

        return new com.javaorm.core.pagination.PageResult<>(
                productPage.getTotal(),
                dtoList,
                productPage.getPageNum(),
                productPage.getPageSize()
        );
    }

    public MallProduct getById(Long id) {
        return orm.select(MallProduct.class)
                .where()
                .eq(MallProduct::getId, id)
                .firstOrNull();
    }

    @Transactional
    public Long create(MallProduct product) {
        product.setCreateTime(new Date());
        product.setUpdateTime(new Date());
        product.setVersion(1);
        product.setDeleted(0);
        if (product.getSales() == null) {
            product.setSales(0);
        }
        if (product.getStatus() == null) {
            product.setStatus(1); // 默认上架
        }
        orm.insert(product).exec();
        return product.getId();
    }

    /**
     * 更新商品信息（展示 @Version 乐观锁自动版本校验）
     */
    @Transactional
    public boolean update(MallProduct product) {
        product.setUpdateTime(new Date());
        return orm.update(product).exec() > 0;
    }

    /**
     * 切换商品上下架状态（使用 JavaORM 灵活的 UpdateBuilder）
     */
    @Transactional
    public boolean toggleStatus(Long id, Integer status) {
        return orm.update(MallProduct.class)
                .set(MallProduct::getStatus, status)
                .set(MallProduct::getUpdateTime, new Date())
                .where()
                .eq(MallProduct::getId, id)
                .execute() > 0;
    }

    /**
     * 逻辑删除商品（MP 注解 @TableLogic 自动生效）
     */
    @Transactional
    public boolean delete(Long id) {
        MallProduct product = getById(id);
        if (product != null) {
            return orm.delete(product).exec() > 0;
        }
        return false;
    }
}
