package com.recommendation.common.repository;

import com.recommendation.common.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    
    List<Category> findByParentIdOrderBySortOrderAsc(Long parentId);
    
    List<Category> findByLevelOrderBySortOrderAsc(Integer level);
    
    Optional<Category> findByNameAndParentId(String name, Long parentId);
    
    @Query("SELECT c FROM Category c WHERE c.parentId = :parentId AND c.status = 1 ORDER BY c.sortOrder ASC, c.id ASC")
    List<Category> findActiveByParentId(@Param("parentId") Long parentId);
    
    @Query("SELECT c FROM Category c WHERE c.level = :level AND c.status = 1 ORDER BY c.sortOrder ASC")
    List<Category> findActiveByLevel(@Param("level") Integer level);
}
