package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.ILogDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.LogRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.log.LogDataOperation;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.LogOperationDTO;
import com.jmal.clouddisk.model.file.FileBaseDTO;
import com.jmal.clouddisk.util.TimeUntils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class LogDAOJpaImpl implements ILogDAO, IWriteCommon<LogOperation> {

    private final LogRepository logRepository;

    private final FileMetadataRepository fileMetadataRepository;

    private final IWriteService writeService;

    @Override
    public void AsyncSaveAll(Iterable<LogOperation> entities) {
        writeService.submit(new LogDataOperation.CreateAll(entities) );
    }

    @Override
    public void save(LogOperation logOperation) {
        writeService.submit(new LogDataOperation.Create(logOperation) );
    }

    @Override
    public Page<LogOperation> findAllByQuery(LogOperationDTO logOperationDTO, String currentUsername, String currentUserId, boolean isCreator) {
        Specification<LogOperation> spec = fromDTO(logOperationDTO, currentUsername, currentUserId, isCreator);
        return logRepository.findAll(spec, logOperationDTO.getPageable());
    }

    @Override
    public long countByUrl(String url) {
        return logRepository.countByUrl(url);
    }

    @Override
    public Page<LogOperation> findFileOperationHistoryByFileId(LogOperationDTO logOperationDTO, String fileId, String currentUserId, String currentUsername) {
        Specification<LogOperation> spec = fileHistoryFor(fileId, currentUserId, currentUsername);
        return logRepository.findAll(spec, logOperationDTO.getPageable());
    }

    public static Specification<LogOperation> fromDTO(LogOperationDTO dto, String currentUsername, String currentUserId, boolean isCreator) {
        return (root, _, criteriaBuilder) -> {

            List<Predicate> predicates = new ArrayList<>();

            // 处理 username 和 excludeUsername
            if (!CharSequenceUtil.isBlank(dto.getUsername())) {
                predicates.add(criteriaBuilder.equal(root.get("username"), dto.getUsername()));
            } else if (!CharSequenceUtil.isBlank(dto.getExcludeUsername())) {
                predicates.add(criteriaBuilder.notEqual(root.get("username"), currentUsername));
            }

            // 处理 ip
            if (!CharSequenceUtil.isBlank(dto.getIp())) {
                predicates.add(criteriaBuilder.equal(root.get("ip"), dto.getIp()));
            }

            // 处理 type
            if (!CharSequenceUtil.isBlank(dto.getType())) {
                predicates.add(criteriaBuilder.equal(root.get("type"), dto.getType()));
            }

            // 处理特殊的权限逻辑
            if (!isCreator && LogOperation.Type.OPERATION_FILE.name().equals(dto.getType())) {
                predicates.add(criteriaBuilder.equal(root.get("fileUserId"), currentUserId));
            }

            // 处理时间范围
            if (dto.getStartTime() != null && dto.getEndTime() != null) {
                LocalDateTime s = TimeUntils.getLocalDateTime(dto.getStartTime());
                LocalDateTime e = TimeUntils.getLocalDateTime(dto.getEndTime());
                predicates.add(criteriaBuilder.between(root.get("createTime"), s, e));
            }

            // 将所有条件用 AND 连接
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public Specification<LogOperation> fileHistoryFor(String fileId, String requestUserId, String requestUsername) {

        Optional<FileBaseDTO> fileOpt = fileMetadataRepository.findFileBaseDTO(fileId);
        if (fileOpt.isEmpty()) {
            return (root, _, cb) -> cb.equal(root.get("id"), "");
        }
        FileBaseDTO fileBaseDTO = fileOpt.get();

        String fileUserId = fileBaseDTO.getUserId();
        String filepath = fileBaseDTO.getPath() + fileBaseDTO.getName();
        String filepathWithoutSlash = fileBaseDTO.getPath().startsWith("/") ? fileBaseDTO.getPath().substring(1) + fileBaseDTO.getName() : filepath;
        String operationFunEndPattern = filepath + "\"";

        return (root, _, cb) -> {
            List<Predicate> mainPredicates = new ArrayList<>();

            Predicate orPredicate = cb.or(
                    cb.equal(root.get("filepath"), filepath),
                    // regex("^...") 翻译为 SQL 的 LIKE '...%'
                    cb.like(root.get("filepath"), filepath + "%"),
                    cb.equal(root.get("filepath"), filepathWithoutSlash),
                    // regex("...\"$") 翻译为 SQL 的 LIKE '%...\"'
                    cb.like(root.get("operationFun"), "%" + operationFunEndPattern)
            );
            mainPredicates.add(orPredicate);

            if (!fileUserId.equals(requestUserId)) {
                mainPredicates.add(cb.equal(root.get("username"), requestUsername));
            }

            mainPredicates.add(cb.equal(root.get("type"), LogOperation.Type.OPERATION_FILE.name()));
            mainPredicates.add(cb.equal(root.get("fileUserId"), fileUserId));

            return cb.and(mainPredicates.toArray(new Predicate[0]));
        };
    }
}
