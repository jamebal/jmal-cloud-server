package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.ocr.OcrConfig;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface OcrConfigRepository extends JpaRepository<OcrConfig, String> {

}
