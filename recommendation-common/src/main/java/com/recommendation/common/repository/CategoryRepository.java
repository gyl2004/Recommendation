package com.recommendation.common.repository;

import com.recommendation.common.entity.Category;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 分类Repository接口
 * 提供分类相关的数据访问操作
 */
@Repository
public interface CategoryRepository extends BaseRepository<Category, Integer> {

    /**
     * 根据分类名称查找分类
     */
    Optional<Category> findByName(String name);

    /**
     * 查找所有启用的分类
     */
    List<Category> findByStatus(Integer status);

    /**
     * 根据父分类ID查找子分类
     */
    List<Category> findByParentIdAndStatusOrderBySortOrderAscIdAsc(Integer parentId, Integer status);

    /**
     * 查找所有顶级分类
     */
    List<Category> findByParentIdAndStatusOrderBySortOrderAscIdAsc(Integer parentId, Integer status);

    /**
     * 根据层级查找分类
     */
    List<Category> findByLevelAndStatusOrderBySortOrderAscIdAsc(Integer level, Integer status);

    /**
     * 查找指定分类的所有子分类(递归)
     */
    @Query(value = "WITH RECURSIVE category_tree AS (" +
                   "  SELECT id, name, parent_id, level, sort_order, status " +
                   "  FROM categories " +
                   "  WHERE parent_id = :parentId AND status = 1 " +
                   "  UNION ALL " +
                   "  SELECT c.id, c.name, c.parent_id, c.level, c.sort_order, c.status " +
                   "  FROM categories c " +
                   "  INNER JOIN category_tree ct ON c.parent_id = ct.id " +
                   "  WHERE c.status = 1" +
                   ") " +
                   "SELECT * FROM category_tree ORDER BY level, sort_order, id", 
           nativeQuery = true)
    List<Category> findAllSubCategories(@Param("parentId") Integer parentId);

    /**
     * 查找分类路径(从根到指定分类)
     */
    @Query(value = "WITH RECURSIVE category_path AS (" +
                   "  SELECT id, name, parent_id, level, sort_order, status " +
                   "  FROM categories " +
                   "  WHERE id = :categoryId " +
                   "  UNION ALL " +
                   "  SELECT c.id, c.name, c.parent_id, c.level, c.sort_order, c.status " +
                   "  FROM categories c " +
                   "  INNER JOIN category_path cp ON c.id = cp.parent_id " +
                   "  WHERE c.parent_id != 0" +
                   ") " +
                   "SELECT * FROM category_path ORDER BY level", 
           nativeQuery = true)
    List<Category> findCategoryPath(@Param("categoryId") Integer categoryId);

    /**
     * 统计分类下的内容数量
     */
    @Query("SELECT COUNT(c) FROM Content c WHERE c.categoryId = :categoryId AND c.status = 'PUBLISHED'")
    long countContentsByCategoryId(@Param("categoryId") Integer categoryId);

    /**
     * 查找有内容的分类
     */
    @Query("SELECT DISTINCT c FROM Category c " +
           "INNER JOIN Content ct ON c.id = ct.categoryId " +
           "WHERE c.status = 1 AND ct.status = 'PUBLISHED'")
    List<Category> findCategoriesWithContent();

    /**
     * 根据分类名称模糊查询
     */
    List<Category> findByNameContainingIgnoreCaseAndStatus(String name, Integer status);

    /**
     * 检查分类名称是否存在(同一父分类下)
     */
    boolean existsByNameAndParentId(String name, Integer parentId);

    /**
     * 查找最大排序值
     */
    @Query("SELECT COALESCE(MAX(c.sortOrder), 0) FROM Category c WHERE c.parentId = :parentId")
    Integer findMaxSortOrderByParentId(@Param("parentId") Integer parentId);

    /**
     * 批量更新分类状态
     */
    @Query("UPDATE Category c SET c.status = :status WHERE c.id IN :categoryIds")
    int updateStatusByIds(@Param("categoryIds") List<Integer> categoryIds, @Param("status") Integer status);

    /**
     * 查找叶子分类(没有子分类的分类)
     */
    @Query("SELECT c FROM Category c WHERE c.status = 1 AND c.id NOT IN " +
           "(SELECT DISTINCT c2.parentId FROM Category c2 WHERE c2.parentId != 0 AND c2.status = 1)")
    List<Category> findLeafCategories();
}