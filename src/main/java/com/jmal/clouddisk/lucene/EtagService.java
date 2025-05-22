package com.jmal.clouddisk.lucene;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.crypto.SecureUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.mongodb.client.result.UpdateResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

/**
 * EtagService
 * 文件和文件夹的 ETag 更新逻辑
 * 异步处理文件变化和删除的 ETag 更新
 * 后台 Worker 处理标记为需要更新 ETag 的文件夹
 * ETag 计算和更新的内部方法
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EtagService {

    private final MongoTemplate mongoTemplate;

    private final FileProperties fileProperties;

    private final IUserService userService;

    private static final String EMPTY_FOLDER_ETAG_BASE_STRING = "EMPTY_FOLDER_REPRESENTATION_MONGO_V2";
    private static final int MAX_ETAG_UPDATE_ATTEMPTS = 5; // 最大失败重试次数

    /**
     * 处理文件夹Etag的线程池
     */
    private ExecutorService executorMarkedFoldersService;

    private final ReentrantLock processMarkedFoldersLock = new ReentrantLock();

    @PostConstruct
    public void init() {
        if (executorMarkedFoldersService == null) {
            executorMarkedFoldersService = ThreadUtil.newFixedExecutor(1, 1, "EtagWorker-", false);
        }
        ThreadUtil.execute(() -> {
            Query queryNoEtagQuery = new Query();
            queryNoEtagQuery.addCriteria(Criteria.where(Constants.ETAG).exists(false).and(Constants.IS_FOLDER).is(true));
            long countOfFoldersWithoutEtag = mongoTemplate.count(queryNoEtagQuery, FileDocument.class);
            if (countOfFoldersWithoutEtag > 0) {
                log.info("{} folders found without ETag. Marking them for ETag calculation.", countOfFoldersWithoutEtag);
                Update update = new Update().set(Constants.NEEDS_ETAG_UPDATE_FIELD, true)
                        .currentDate(Constants.LAST_ETAG_UPDATE_REQUEST_AT_FIELD);
                queryNoEtagQuery.addCriteria(Criteria.where(Constants.IS_FOLDER).is(true));
                mongoTemplate.updateMulti(queryNoEtagQuery, update, FileDocument.class);
            }
            processRootFolderFiles();
            startProcessMarkedFolders();
        });
    }

    /**
     * 当文件内容发生变化（上传/修改/重命名/删除等）时调用。
     * 立即更新文件的ETag，并异步标记其父文件夹需要更新ETag。
     *
     * @param file 发生变化的文件
     */
    public void updateFileEtagAsync(String username, File file) {
        if (!FileUtil.exist(file)) {
            return;
        }
        if (FileUtil.isFile(file)) {
            updateFileEtagAndMarkParentAsync(username, file);
        }
    }

    private void startProcessMarkedFolders() {
        executorMarkedFoldersService.execute(() -> {
            processMarkedFoldersLock.lock();
            try {
                processMarkedFolders("markedFoldersWorker-" + Thread.currentThread().getName());
            } finally {
                processMarkedFoldersLock.unlock();
            }
        });
    }

    /**
     * 当文件内容发生变化（上传新版本/修改）时调用。
     * 立即更新文件的ETag，并异步标记其父文件夹需要更新ETag。
     *
     * @param file 发生变化的文件
     */
    private String updateFileEtagAndMarkParentAsync(String username, File file) {
        try {
            if (!FileUtil.exist(file) || !FileUtil.isFile(file)) {
                return null;
            }
            String newEtag = SecureUtil.sha256(file);

            String fileName = file.getName();
            String relativePath = getDbPath(username, file);
            if (relativePath == null) {
                return null;
            }
            String userId = userService.getUserIdByUserName(username);
            Query query = getQueryByPath(userId, relativePath, fileName);
            query.fields().include(Constants.ETAG);
            FileDocument currentFileDoc = mongoTemplate.findOne(query, FileDocument.class);
            String oldEtag = (currentFileDoc != null) ? currentFileDoc.getEtag() : null;

            if (newEtag != null && !newEtag.equals(oldEtag)) {
                Update update = new Update().set(Constants.ETAG, newEtag);
                // 如果有版本控制字段，这里也应该更新
                mongoTemplate.updateFirst(query, update, FileDocument.class);
                log.info("File ETag updated for {}: {} -> {}", relativePath, oldEtag, newEtag);

                // 标记父文件夹需要更新ETag
                markFolderForEtagUpdate(userId, relativePath);
            } else {
                log.debug("File ETag for {} did not change or calculation failed.", file.getAbsoluteFile());
            }
            return newEtag;
        } catch (Exception e) {
            log.error("Error updating file ETag for {}: {}", file.getAbsoluteFile(), e.getMessage(), e);
        }
        return null;
    }

    /**
     * 获取改文件在数据库中的路径(PATH_FIELD)字段的值
     *
     * @param username     用户名
     * @param physicalFile 文件
     */
    private String getDbPath(String username, File physicalFile) {
        Path physicalPath = physicalFile.toPath();
        Path userRootPath = Paths.get(fileProperties.getRootDir(), username);

        if (!physicalPath.startsWith(userRootPath)) {
            return null;
        }

        Path relativeNioPath = userRootPath.relativize(physicalPath);

        if (relativeNioPath.toString().isEmpty()) {
            return null;
        }

        if (relativeNioPath.getNameCount() <= 1 || relativeNioPath.getParent() == null) {
            return "/";
        }
        return "/" + relativeNioPath.getParent().toString().replace(File.separatorChar, '/') + "/";
    }

    /**
     * 当新文件夹创建或重命名时调用。
     * 立即设置新文件夹的初始ETag，并异步标记其父文件夹需要更新ETag。
     *
     * @param username          用户名
     * @param physicalNewFolder 新创建的文件夹的完整路径
     */
    public void handleNewFolderCreationAsync(String username, File physicalNewFolder) {
        if (!FileUtil.exist(physicalNewFolder) || !FileUtil.isDirectory(physicalNewFolder)) {
            return;
        }
        String fileName = physicalNewFolder.getName();
        String relativePath = getDbPath(username, physicalNewFolder);
        if (relativePath == null) {
            return;
        }
        try {
            String userId = userService.getUserIdByUserName(username);

            // 查询当前文件夹下是否有内容, 由于是异步处理，刚创建的文件夹可能还没来得及设置etag, 其下就有新文件了, 所有需要查询一次
            String currentFolderNormalizedPath = relativePath + fileName + "/";
            Query childrenQuery = Query.query(Criteria.where(Constants.PATH_FIELD).is(currentFolderNormalizedPath));
            childrenQuery.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
            boolean hasChildren = mongoTemplate.exists(childrenQuery, FileDocument.class);
            if (!hasChildren) {
                // 如果没有内容，则设置初始ETag
                String initialEtag = hashString(EMPTY_FOLDER_ETAG_BASE_STRING);
                Query query = getQueryByPath(userId, relativePath, fileName);
                Update update = new Update().set(Constants.ETAG, initialEtag).set(Constants.NEEDS_ETAG_UPDATE_FIELD, false);
                mongoTemplate.updateFirst(query, update, FileDocument.class);
                log.info("Initial ETag set for new folder {}: {}", relativePath, initialEtag);
            }
            // 标记父文件夹需要更新ETag
            markFolderForEtagUpdate(userService.getUserIdByUserName(username), relativePath);
        } catch (NoSuchAlgorithmException e) {
            log.error("Algorithm error setting initial ETag for folder {}: {}", relativePath, e.getMessage(), e);
        } catch (DataAccessException e) {
            log.error("Database error setting initial ETag for folder {}: {}", relativePath, e.getMessage(), e);
        }
    }

    /**
     * 通过 userId 和 dbPath 还有filename 可获取到唯一的文件/文件夹
     *
     * @param userId   用户id
     * @param dbPath   dbPath
     * @param filename 文件名
     */
    private static Query getQueryByPath(String userId, String dbPath, String filename) {
        Query query = Query.query(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where(Constants.PATH_FIELD).is(dbPath));
        query.addCriteria(Criteria.where(Constants.FILENAME_FIELD).is(filename));
        return query;
    }

    /**
     * 当文件或文件夹被删除时调用。
     * 异步标记其原父文件夹需要更新ETag。
     *
     * @param username 用户名
     * @param file     被删除的文件
     */
    public void handleItemDeletionAsync(String username, File file) {
        log.info("Handling ETag update for parent of deleted item: {}", file.getAbsoluteFile());
        String relativePath = getDbPath(username, file);
        if (relativePath == null) {
            return;
        }

        // 标记父文件夹需要更新ETag
        markFolderForEtagUpdate(userService.getUserIdByUserName(username), relativePath);
    }

    /**
     * 处理根目录下文件的ETag
     */
    private void processRootFolderFiles() {
        boolean run = true;
        while (run) {
            Query findQuery = Query.query(Criteria.where(Constants.ETAG).exists(false).and(Constants.IS_FOLDER).is(false).and(Constants.PATH_FIELD).is("/")).limit(16);
            findQuery.fields().include(Constants.PATH_FIELD, Constants.FILENAME_FIELD, IUserService.USER_ID, Constants.ETAG);
            long count = mongoTemplate.count(findQuery, FileDocument.class);
            if (count == 0) {
                run = false;
            }

            // 获取根据下所有的文件
            List<FileDocument> tasks = mongoTemplate.find(findQuery, FileDocument.class);

            if (tasks.isEmpty()) {
                continue;
            }
            log.debug("Found {} files marked for ETag update. Processing...", tasks.size());
            for (FileDocument fileDoc : tasks) {
                String username = userService.getUserNameById(fileDoc.getUserId());
                Path path = Paths.get(fileProperties.getRootDir(), username, fileDoc.getPath(), fileDoc.getName());

                if (Files.exists(path)) {
                    updateFileEtagAndMarkParentAsync(username, path.toFile());
                }
            }
        }
    }

    /**
     * 由后台Worker调用，处理标记为需要更新ETag的文件夹。
     * 每次处理一定数量的文件夹。
     *
     * @param workerId 当前worker的唯一标识，用于任务锁定（如果实现）
     */
    private void processMarkedFolders(String workerId) {
        boolean run = true;
        while (run) {
            Query findQuery = Query.query(Criteria.where(Constants.NEEDS_ETAG_UPDATE_FIELD).is(true).and(Constants.IS_FOLDER).is(true)).with(Sort.by(Sort.Direction.ASC, Constants.LAST_ETAG_UPDATE_REQUEST_AT_FIELD)).limit(16);
            findQuery.fields().include(Constants.PATH_FIELD, Constants.FILENAME_FIELD, IUserService.USER_ID, Constants.ETAG);
            long count = mongoTemplate.count(findQuery, FileDocument.class);
            if (count == 0) {
                log.info("[Worker {}] No folders marked for ETag update. Exiting.", workerId);
                run = false;
            }

            // 简单的任务获取，没有复杂的锁定机制，适合单worker实例或能接受少量重复计算的场景
            List<FileDocument> tasks = mongoTemplate.find(findQuery, FileDocument.class);

            if (tasks.isEmpty()) {
                continue;
            }
            log.info("[Worker {}] Found {} folders marked for ETag update. Processing...", workerId, tasks.size());

            for (FileDocument folderDoc : tasks) {
                String folderPath = folderDoc.getPath();
                String docId = folderDoc.getId();
                String userId = folderDoc.getUserId();
                log.debug("[Worker {}] Processing ETag for folder: {} (ID: {})", workerId, folderPath, docId);

                try {
                    boolean etagActuallyChanged = calculateAndUpdateSingleFolderEtagInternal(folderDoc, workerId);

                    // 清除标记 (无论ETag是否变化，只要处理过程没有抛出不可恢复的异常)
                    Query clearMarkQuery = Query.query(Criteria.where("_id").is(docId));
                    Update clearMarkUpdate = new Update().set(Constants.NEEDS_ETAG_UPDATE_FIELD, false).unset(Constants.ETAG_UPDATE_FAILED_ATTEMPTS_FIELD) // 成功后清除失败尝试
                            .unset(Constants.LAST_ETAG_UPDATE_ERROR_FIELD);
                    mongoTemplate.updateFirst(clearMarkQuery, clearMarkUpdate, FileDocument.class);
                    log.debug("[Worker {}] Cleared ETag update mark for folder: {}", workerId, folderPath);

                    if (etagActuallyChanged) {
                        // 如果ETag变化了，标记其父文件夹
                        markFolderForEtagUpdate(userId, folderPath);
                    }
                } catch (Exception e) { // 捕获所有可能的异常，防止worker线程中断
                    log.error("[Worker {}] Critical error processing ETag for folder {}: {}", workerId, folderPath, e.getMessage(), e);
                    handleProcessingError(docId, folderPath, e, workerId);
                }
            }
            log.debug("[Worker {}] Finished processing batch of {} folders.", workerId, tasks.size());
        }
    }

    /**
     * 标记指定文件夹需要ETag更新
     *
     * @param userId          用户ID
     * @param currentFolderPath 当前文件夹路径
     */
    private void markFolderForEtagUpdate(String userId, String currentFolderPath) {
        String parentFolderPath = getParentDbPath(currentFolderPath);
        if (parentFolderPath == null) {
            return;
        }
        String folderName = Paths.get(currentFolderPath).getFileName().toString();
        Query query = getQueryByPath(userId, parentFolderPath, folderName);
        Update update = new Update().set(Constants.NEEDS_ETAG_UPDATE_FIELD, true).currentDate(Constants.LAST_ETAG_UPDATE_REQUEST_AT_FIELD);

        try {
            UpdateResult result = mongoTemplate.updateFirst(query, update, FileDocument.class);
            if (result.getModifiedCount() > 0 || result.getMatchedCount() > 0) { // 即使已经是true也更新时间戳
                log.info("Marked folder for ETag update: {}", currentFolderPath);
                startProcessMarkedFolders();
            } else {
                log.warn("Folder not found to mark for ETag update: {}", currentFolderPath);
            }
        } catch (DataAccessException e) {
            log.error("DB error marking folder for ETag update {}: {}", currentFolderPath, e.getMessage(), e);
        }
    }

    /**
     * 内部方法：计算并更新单个文件夹的ETag。
     * 被 processMarkedFolders 调用。
     *
     * @return true 如果ETag实际发生了变化并被更新
     */
    private boolean calculateAndUpdateSingleFolderEtagInternal(FileDocument folderDoc, String workerId) throws NoSuchAlgorithmException {
        String folderPath = folderDoc.getPath();
        String oldEtag = folderDoc.getEtag();
        String userId = folderDoc.getUserId();
        String newCalculatedEtag;

        String currentFolderNormalizedPath = folderDoc.getPath() + folderDoc.getName() + "/";
        Query childrenQuery = Query.query(Criteria.where(Constants.PATH_FIELD).is(currentFolderNormalizedPath));
        childrenQuery.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        childrenQuery.fields().include(Constants.FILENAME_FIELD).include(Constants.ETAG).include(Constants.IS_FOLDER).include(IUserService.USER_ID).include(Constants.PATH_FIELD);
        List<FileDocument> children = mongoTemplate.find(childrenQuery, FileDocument.class);

        if (children.isEmpty()) {
            newCalculatedEtag = hashString(EMPTY_FOLDER_ETAG_BASE_STRING);
        } else {
            List<String> childRepresentations = children.stream().sorted(Comparator.comparing(FileDocument::getName)) // 按名称排序
                    .map(child -> formatChildRepresentation(workerId, child)).toList();
            StringBuilder combinedRepresentation = new StringBuilder();
            for (String rep : childRepresentations) {
                if (rep == null) {
                    return false;
                }
                if (!rep.isBlank()) {
                    combinedRepresentation.append(rep).append(";");
                }
            }
            newCalculatedEtag = hashString(combinedRepresentation.toString());
        }

        if (!newCalculatedEtag.equals(oldEtag)) {
            Query updateQuery = Query.query(Criteria.where("_id").is(folderDoc.getId()));
            // 如果使用乐观锁，这里也需要检查版本号
            // query.addCriteria(Criteria.where("_version").is(folderDoc.getVersion()));
            Update update = new Update().set(Constants.ETAG, newCalculatedEtag);
            // update.inc("_version", 1);
            UpdateResult result = mongoTemplate.updateFirst(updateQuery, update, FileDocument.class);

            if (result.getModifiedCount() > 0) {
                log.info("[Worker {}] Folder ETag updated for {}: {} -> {}", workerId, folderPath, oldEtag, newCalculatedEtag);
                return true;
            } else {
                // 可能在计算和写入之间，该文档被其他worker处理了（如果ETag已更新为相同值）或版本冲突
                log.warn("[Worker {}] Folder ETag for {} was calculated as {} (old: {}), but DB update reported no modification. Possible race condition or already up-to-date.", workerId, folderPath, newCalculatedEtag, oldEtag);
                return false; // 没有实际修改数据库
            }
        }
        log.debug("[Worker {}] Folder ETag for {} did not change (calculated: {}, current: {}).", workerId, folderPath, newCalculatedEtag, oldEtag);
        return false;
    }

    private void handleProcessingError(String docId, String folderPath, Exception e, String workerId) {
        Query query = Query.query(Criteria.where("_id").is(docId));
        FileDocument failedDoc = mongoTemplate.findOne(query, FileDocument.class);
        int attempts = (failedDoc != null && failedDoc.getEtagUpdateFailedAttempts() != null) ? failedDoc.getEtagUpdateFailedAttempts() + 1 : 1;

        Update update = new Update().set(Constants.LAST_ETAG_UPDATE_ERROR_FIELD, e.getMessage().substring(0, Math.min(e.getMessage().length(), 1000))) // 限制错误信息长度
                .set(Constants.ETAG_UPDATE_FAILED_ATTEMPTS_FIELD, attempts);

        if (attempts >= MAX_ETAG_UPDATE_ATTEMPTS) {
            update.set(Constants.NEEDS_ETAG_UPDATE_FIELD, false); // 停止重试
            log.error("[Worker {}] Folder {} ETag update failed after {} attempts. Giving up.", workerId, folderPath, attempts);
        } else {
            // 可以选择让它下次被重新拾取 (needsEtagUpdate 保持 true)
            log.warn("[Worker {}] Folder {} ETag update failed (attempt {}). Will retry.", workerId, folderPath, attempts);
        }
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }

    private static String getParentDbPath(String currentFolderPath) {
        if (CharSequenceUtil.isBlank(currentFolderPath) || currentFolderPath.equals("/")) {
            return null; // 根目录没有父目录，或者已经是根
        }
        // 确保路径以 "/" 开头和结尾
        if (!currentFolderPath.endsWith("/") || !currentFolderPath.startsWith("/")) {
            return null;
        }

        // 移除尾部的斜杠以便找到倒数第二个斜杠
        String pathWithoutTrailingSlash = currentFolderPath.substring(0, currentFolderPath.length() - 1);
        int lastSlashIndex = pathWithoutTrailingSlash.lastIndexOf('/');

        if (lastSlashIndex < 0) { // 例如 "folder/" 这种情况，父路径是 "/"
            return "/";
        }
        if (lastSlashIndex == 0) { // 例如 "/folder/", 父路径是 "/"
            return "/";
        }
        // 例如 "/A/B/", pathWithoutTrailingSlash = "/A/B", lastSlashIndex 在 A 后面
        // substring(0, lastSlashIndex + 1) 会得到 "/A/"
        return pathWithoutTrailingSlash.substring(0, lastSlashIndex + 1);
    }

    private String formatChildRepresentation(String workerId, FileDocument child) {
        if (child.getEtag() == null) {
            String username = userService.getUserNameById(child.getUserId());
            Path path = Paths.get(fileProperties.getRootDir(), username, child.getPath(), child.getName());
            if (Files.exists(path)) {
                // 只对文件执行此操作
                if (!child.getIsFolder()) {
                    return updateFileEtagAndMarkParentAsync(username, path.toFile());
                } else {
                    // 子文件夹ETag为null，父文件夹本次计算中止
                    return null;
                }
            } else {
                log.warn("[Worker {}] File {} does not exist. Skipping.", workerId, path);
                return "";
            }
        }
        return child.getName() + ":" + child.getEtag();
    }

    private String hashString(String input) throws NoSuchAlgorithmException {
        return SecureUtil.sha256(input);
    }

    @PreDestroy
    public void cleanup() {
        if (executorMarkedFoldersService != null) {
            executorMarkedFoldersService.shutdown();
        }
    }

}
