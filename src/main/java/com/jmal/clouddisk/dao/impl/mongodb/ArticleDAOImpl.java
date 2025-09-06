package com.jmal.clouddisk.dao.impl.mongodb;

import com.jmal.clouddisk.dao.IArticleDAO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class ArticleDAOImpl implements IArticleDAO {

}
