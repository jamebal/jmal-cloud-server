package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IEtagDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileEtagRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.etag.EtagOperation;
import com.jmal.clouddisk.dao.util.MyQuery;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.file.dto.FileBaseEtagDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class EtagDAOJpaImpl implements IEtagDAO {

    private final IWriteService writeService;
    private final FileEtagRepository fileEtagRepository;

    @Override
    public long countFoldersWithoutEtag() {
        return fileEtagRepository.countByEtagIsNullAndIsFolderIsTrue();
    }

    @Override
    public void setFoldersWithoutEtag() {
        try {
            int modified = writeService.submit(new EtagOperation.SetFoldersWithoutEtag()).get(15, TimeUnit.SECONDS);
            if (modified < 1) {
                log.warn("没有需要设置 ETag 的文件夹");
            }
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public long getFolderSize(String userId, String path) {
        String pathPrefixForLike = MyQuery.escapeLikeSpecialChars(path) + "%";
        return fileEtagRepository.sumSizeByUserIdAndPathPrefix(userId, pathPrefixForLike);
    }

    @Override
    public boolean existsByNeedsEtagUpdateFolder() {
        return fileEtagRepository.existsByNeedsEtagUpdateIsTrueAndIsFolderIsTrue();
    }

    @Override
    public String findEtagByUserIdAndPathAndName(String userId, String path, String name) {
        return fileEtagRepository.findEtagByUserIdAndPathAndName(userId, path, name).orElse(null);
    }

    @Override
    public void setEtagByUserIdAndPathAndName(String userId, String path, String name, String newEtag) {
        try {
            writeService.submit(new EtagOperation.SetEtagByUserIdAndPathAndName(userId, path, name, newEtag)).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public boolean existsByUserIdAndPath(String userId, String path) {
        return fileEtagRepository.existsByUserIdAndPath(userId, path);
    }

    @Override
    public long countRootDirFilesWithoutEtag() {
        return fileEtagRepository.countByEtagIsNullAndIsFolderIsFalseAndPath("/");
    }

    @Override
    public List<FileBaseEtagDTO> findFileBaseEtagDTOByRootDirFilesWithoutEtag() {
        Pageable pageable = PageRequest.of(0, 16);
        return fileEtagRepository.findFileBaseEtagDTOByEtagIsNullAndIsFolderIsFalseAndPath("/", pageable);
    }

    @Override
    public List<FileBaseEtagDTO> findFileBaseEtagDTOByNeedUpdateFolder(Sort sort) {
        Pageable pageable = PageRequest.of(0, 16, sort);
        return fileEtagRepository.findFileBaseEtagDTOByNeedUpdateFolder(pageable);
    }

    @Override
    public void clearMarkUpdateById(String fileId) {
        try {
            writeService.submit(new EtagOperation.ClearMarkUpdateById(fileId)).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public boolean setMarkUpdateByUserIdAndPathAndName(String userId, String path, String name) {
        try {
            int modifiedCount = writeService.submit(new EtagOperation.SetMarkUpdateByUserIdAndPathAndName(userId, path, name)).get(30, TimeUnit.SECONDS);
            if (modifiedCount < 1) {
                return fileEtagRepository.existsByUserIdAndPathAndName(userId, path, name);
            }
            return true;
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public List<FileBaseEtagDTO> findFileBaseEtagDTOByUserIdAndPath(String userId, String path) {
        return fileEtagRepository.findFileBaseEtagDTOByUserIdAndPath(userId, path);
    }

    @Override
    public long updateEtagAndSizeById(String fileId, String etag, long size) {
        try {
            return writeService.submit(new EtagOperation.UpdateEtagAndSizeById(fileId, etag, size)).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public int findEtagUpdateFailedAttemptsById(String fileId) {
        return fileEtagRepository.findEtagUpdateFailedAttemptsById(fileId).orElse(1);
    }

    @Override
    public void setFailedEtagById(String fileId, int attempts, String errorMsg, Boolean needsEtagUpdate) {
        try {
            writeService.submit(new EtagOperation.SetFailedEtagById(fileId, attempts, errorMsg, needsEtagUpdate)).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }
}
