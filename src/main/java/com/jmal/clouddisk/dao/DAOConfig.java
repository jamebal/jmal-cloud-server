package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.config.jpa.DataSourceProperties;
import com.jmal.clouddisk.dao.impl.jpa.*;
import com.jmal.clouddisk.dao.impl.mongodb.*;
import com.jmal.clouddisk.dao.repository.jpa.*;
import com.jmal.clouddisk.dao.repository.mongo.FileDocumentRepository;
import com.jmal.clouddisk.dao.write.IWriteService;
import com.jmal.clouddisk.lucene.LuceneQueryService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class DAOConfig {

    private final Environment environment;
    private final ApplicationContext applicationContext;
    private final FileProperties fileProperties;
    private final LuceneQueryService luceneQueryService;
    private final DataSourceProperties dataSourceProperties;

    public DAOConfig(Environment environment, ApplicationContext applicationContext, FileProperties fileProperties, LuceneQueryService luceneQueryService, DataSourceProperties dataSourceProperties) {
        this.environment = environment;
        this.applicationContext = applicationContext;
        this.fileProperties = fileProperties;
        this.luceneQueryService = luceneQueryService;
        this.dataSourceProperties = dataSourceProperties;
    }


    @Bean
    public FilePersistenceService filePersistenceService() {
        return new FilePersistenceService(fileProperties);
    }

    @Bean
    public FilePropsDAO filePropsDAO() {
        if (isIsRelational()) {
            FilePropsRepository filePropsRepository = applicationContext.getBean(FilePropsRepository.class);
            IWriteService iWriteService = applicationContext.getBean(IWriteService.class);
            return new FilePropsDAO(filePropsRepository, luceneQueryService, iWriteService, dataSourceProperties);
        }
        return null;
    }

    @Bean
    public IArticleDAO articleDAO() {
        if (isIsRelational()) {
            NamedParameterJdbcTemplate jdbcTemplate = applicationContext.getBean(NamedParameterJdbcTemplate.class);
            ArticleRepository articleRepository = applicationContext.getBean(ArticleRepository.class);
            FilePersistenceService filePersistenceService = applicationContext.getBean(FilePersistenceService.class);
            FileMetadataRepository fileMetadataRepository = applicationContext.getBean(FileMetadataRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new ArticleDAOJpaImpl(
                    dataSourceProperties,
                    jdbcTemplate,
                    articleRepository,
                    filePersistenceService,
                    fileMetadataRepository,
                    fileProperties,
                    luceneQueryService,
                    writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            FileDocumentRepository fileDocumentRepository = applicationContext.getBean(FileDocumentRepository.class);
            IUserService userService = applicationContext.getBean(IUserService.class);
            CommonFileService commonFileService = applicationContext.getBean(CommonFileService.class);

            return new ArticleDAOImpl(mongoTemplate,
                    fileDocumentRepository,
                    userService,
                    commonFileService,
                    fileProperties,
                    luceneQueryService);
        }
    }

    @Bean
    public ILogDAO logDAO() {
        if (isIsRelational()) {
            LogRepository logRepository = applicationContext.getBean(LogRepository.class);
            FileMetadataRepository fileMetadataRepository = applicationContext.getBean(FileMetadataRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new LogDAOJpaImpl(logRepository, fileMetadataRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new LogDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public IAccessTokenDAO accessTokenDAO() {
        if (isIsRelational()) {
            AccessTokenRepository accessTokenRepository = applicationContext.getBean(AccessTokenRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new AccessTokenDAOJpaImpl(accessTokenRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new AccessTokenDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public ICategoryDAO categoryDAO() {
        if (isIsRelational()) {
            CategoryRepository categoryRepository = applicationContext.getBean(CategoryRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new CategoryDAOJpaImpl(categoryRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new CategoryDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public IDirectLinkDAO directLinkDAO() {
        if (isIsRelational()) {
            DirectLinkRepository directLinkRepository = applicationContext.getBean(DirectLinkRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new DirectLinkDAOJpaImpl(directLinkRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new DirectLinkDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public IEtagDAO etagDAO() {
        if (isIsRelational()) {
            FileEtagRepository fileEtagRepository = applicationContext.getBean(FileEtagRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new EtagDAOJpaImpl(fileEtagRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new EtagDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public IFileDAO fileDAO() {
        if (isIsRelational()) {
            FileMetadataRepository fileMetadataRepository = applicationContext.getBean(FileMetadataRepository.class);
            ArticleRepository articleRepository = applicationContext.getBean(ArticleRepository.class);
            FilePropsDAO filePropsDAO = applicationContext.getBean(FilePropsDAO.class);
            FilePersistenceService filePersistenceService = applicationContext.getBean(FilePersistenceService.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new FileDAOJpaImpl(fileMetadataRepository, articleRepository, filePropsDAO, filePersistenceService, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            UserLoginHolder userLoginHolder = applicationContext.getBean(UserLoginHolder.class);
            return new FileDAOImpl(mongoTemplate, userLoginHolder);
        }
    }

    @Bean
    public IFileHistoryDAO fileHistoryDAO() {
        if (isIsRelational()) {
            FileHistoryRepository fileHistoryRepository = applicationContext.getBean(FileHistoryRepository.class);
            FilePersistenceService filePersistenceService = applicationContext.getBean(FilePersistenceService.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new FileHistoryDAOJpaImpl(fileHistoryRepository, filePersistenceService, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            GridFsTemplate fsTemplate = applicationContext.getBean(GridFsTemplate.class);
            return new FileHistoryDAOImpl(mongoTemplate, fsTemplate);
        }
    }

    @Bean
    public IFileQueryDAO fileQueryDAO() {
        if (isIsRelational()) {
            FileMetadataRepository fileMetadataRepository = applicationContext.getBean(FileMetadataRepository.class);
            FilePersistenceService filePersistenceService = applicationContext.getBean(FilePersistenceService.class);
            LuceneQueryService luceneQueryService = applicationContext.getBean(LuceneQueryService.class);
            TrashRepository trashRepository = applicationContext.getBean(TrashRepository.class);
            return new FileQueryDAOJpaImpl(fileMetadataRepository, filePersistenceService, luceneQueryService, trashRepository, fileProperties);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new FileQueryDAOImpl(mongoTemplate, fileProperties);
        }
    }

    @Bean
    public IFolderSizeDAO folderSizeDAO() {
        if (isIsRelational()) {
            FolderSizeRepository folderSizeRepository = applicationContext.getBean(FolderSizeRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new FolderSizeDAOJpaImpl(folderSizeRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new FolderSizeDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public IHeartwingsDAO heartwingsDAO() {
        if (isIsRelational()) {
            HeartwingsRepository heartwingsRepository = applicationContext.getBean(HeartwingsRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new HeartwingsDAOJpaImpl(heartwingsRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new HeartwingsDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public ILdapConfigDAO ldapConfigDAO() {
        if (isIsRelational()) {
            LdapConfigRepository ldapConfigRepository = applicationContext.getBean(LdapConfigRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new LdapConfigDAOJpaImpl(ldapConfigRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new LdapConfigDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public IMenuDAO menuDAO() {
        if (isIsRelational()) {
            MenuRepository menuRepository = applicationContext.getBean(MenuRepository.class);
            RoleRepository roleRepository = applicationContext.getBean(RoleRepository.class);
            UserRepository userRepository = applicationContext.getBean(UserRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new MenuDAOJpaImpl(menuRepository, roleRepository, userRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new MenuDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public IOcrConfigDAO ocrConfigDAO() {
        if (isIsRelational()) {
            OcrConfigRepository ocrConfigRepository = applicationContext.getBean(OcrConfigRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new OcrConfigDAOJpaImpl(ocrConfigRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new OcrConfigDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public IOfficeConfigDAO officeConfigDAO() {
        if (isIsRelational()) {
            OfficeConfigRepository ocrConfigRepository = applicationContext.getBean(OfficeConfigRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new OfficeConfigDAOJpaImpl(ocrConfigRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new OfficeConfigDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public IOssConfigDAO ossConfigDAO() {
        if (isIsRelational()) {
            OssConfigRepository ossConfigRepository = applicationContext.getBean(OssConfigRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new OssConfigDAOJpaImpl(ossConfigRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new OssConfigDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public IRoleDAO roleDAO() {
        if (isIsRelational()) {
            RoleRepository roleRepository = applicationContext.getBean(RoleRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new RoleDAOJpaImpl(roleRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new RoleDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public ISearchHistoryDAO searchHistoryDAO() {
        if (isIsRelational()) {
            SearchHistoryRepository searchHistoryRepository = applicationContext.getBean(SearchHistoryRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new SearchHistoryJpaImpl(searchHistoryRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new SearchHistoryDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public IShareDAO shareDAO() {
        if (isIsRelational()) {
            ShareRepository shareRepository = applicationContext.getBean(ShareRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new ShareDAOJpaImpl(shareRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new ShareDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public ITagDAO tagDAO() {
        if (isIsRelational()) {
            TagRepository tagRepository = applicationContext.getBean(TagRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new TagDAOJpaImpl(tagRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new TagDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public ITranscodeConfigDAO transcodeConfigDAO() {
        if (isIsRelational()) {
            TranscodeConfigRepository transcodeConfigRepository = applicationContext.getBean(TranscodeConfigRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new TranscodeConfigDAOJpaImpl(transcodeConfigRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new TranscodeConfigDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public ITrashDAO trashDAO() {
        if (isIsRelational()) {
            TrashRepository trashRepository = applicationContext.getBean(TrashRepository.class);
            FileMetadataRepository fileMetadataRepository = applicationContext.getBean(FileMetadataRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new TrashDAOJpaImpl(trashRepository, fileMetadataRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new TrashDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public IUserDAO userDAO() {
        if (isIsRelational()) {
            UserRepository userRepository = applicationContext.getBean(UserRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new UserDAOJpaImpl(userRepository, dataSourceProperties, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new UserDAOImpl(mongoTemplate);
        }
    }

    @Bean
    public IWebsiteSettingDAO websiteSettingDAO() {
        if (isIsRelational()) {
            WebsiteSettingRepository websiteSettingRepository = applicationContext.getBean(WebsiteSettingRepository.class);
            IWriteService writeService = applicationContext.getBean(IWriteService.class);
            return new WebsiteSettingDAOJpaImpl(websiteSettingRepository, writeService);
        } else {
            MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
            return new WebsiteSettingDAOImpl(mongoTemplate);
        }
    }

    private boolean isIsRelational() {
        String dataSourceTypeStr = environment.getProperty("jmalcloud.datasource.type");
        DataSourceType dataSourceType = DataSourceType.fromCode(dataSourceTypeStr);
        return dataSourceType.isRelational();
    }
}
