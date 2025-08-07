package com.recommendation.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;

/**
 * 基础Repository接口
 * 提供通用的CRUD操作和规格查询功能
 * 
 * @param <T> 实体类型
 * @param <ID> 主键类型
 */
@NoRepositoryBean
public interface BaseRepository<T, ID extends Serializable> 
    extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {
}