package com.jmal.clouddisk.lucene;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ObjectUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.dao.IEtagDAO;
import com.jmal.clouddisk.model.file.dto.FileBaseEtagDTO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.impl.CommonUserService;
import com.jmal.clouddisk.util.HashUtil;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final IEtagDAO etagDAO;

    private final FileProperties fileProperties;

    private final CommonUserService userService;

    private static final String EMPTY_FOLDER_ETAG_BASE_STRING = "EMPTY_FOLDER_REPRESENTATION_MONGO_V2";
    private static final int MAX_ETAG_UPDATE_ATTEMPTS = 5; // 最大失败重试次数

    private final AtomicBoolean processingScheduled = new AtomicBoolean(false);

    /**
     * 处理文件夹Etag的线程池
     */
    private ExecutorService executorMarkedFoldersService;

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationReady(ContextRefreshedEvent event) {
        if (event.getApplicationContext().getParent() != null) {
            return;
        }
        ThreadUtil.execute(this::init);
    }

    public void init() {
        if (executorMarkedFoldersService == null) {
            executorMarkedFoldersService = ThreadUtil.newFixedExecutor(1, 1, "EtagWorker-", false);
        }
        Completable.fromAction(() -> {
            long countOfFoldersWithoutEtag = etagDAO.countFoldersWithoutEtag();
            if (countOfFoldersWithoutEtag > 0) {
                log.info("{} folders found without ETag. Marking them for ETag calculation.", countOfFoldersWithoutEtag);
                etagDAO.setFoldersWithoutEtag();
            }
            processRootFolderFiles();
            log.debug("Initial ETag setup finished. Ensuring ETag processing worker is active if needed.");
            ensureProcessingMarkedFolders();
        }).subscribeOn(Schedulers.io())
                .subscribe();
    }

    /**
     * 统计文件夹的大小
     */
    public long getFolderSize(String userId, String path) {
        return etagDAO.getFolderSize(userId, path);
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

    /**
     * 确保 ETag 处理任务已调度或正在运行。
     * 如果当前没有任务在执行或计划执行，则提交一个新任务到执行器。
     * 这个方法是幂等的，多次调用（如果任务已调度）不会产生副作用。
     */
    private void ensureProcessingMarkedFolders() {
        // 使用 compareAndSet 来原子性地检查并设置标志
        // 如果 processingScheduled 为 false，则将其设置为 true 并执行提交逻辑
        if (processingScheduled.compareAndSet(false, true)) {
            log.debug("ETag processing task for marked folders is being scheduled.");
            executorMarkedFoldersService.execute(() -> {
                String workerId = "markedFoldersWorker-" + Thread.currentThread().getName();
                try {
                    // 调用实际的循环处理逻辑
                    processMarkedFoldersLoop(workerId);
                } finally {
                    // 当任务完成（无论是正常结束还是异常结束）后，
                    // 将标志重置为 false，允许后续的 ensureProcessingMarkedFolders 调用再次调度任务。
                    processingScheduled.set(false);
                    log.debug("[Worker {}] ETag processing task finished. Resetting schedule flag.", workerId);

                    // 关键：检查在本次处理运行期间是否有新的文件夹被标记
                    // 如果有，则再次尝试调度，确保它们得到处理
                    if (hasMoreMarkedFoldersInDb()) {
                        log.debug("[Worker {}] New folders were marked during or after the last processing run. Re-triggering ETag processing.", workerId);
                        ensureProcessingMarkedFolders();
                    } else {
                        log.debug("[Worker {}] No more marked folders found after processing run. ETag worker will rest until new marks.", workerId);
                    }
                }
            });
        } else {
            // 如果 processingScheduled 已经是 true，说明任务已在队列中或正在运行
            log.debug("ETag processing task is already scheduled or running. This trigger call is a no-op.");
        }
    }

    /**
     * 检查数据库中是否还有需要更新ETag的文件夹。
     */
    private boolean hasMoreMarkedFoldersInDb() {
        return etagDAO.existsByNeedsEtagUpdateFolder();
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
            String newEtag = HashUtil.sha256(file);

            String fileName = file.getName();
            String relativePath = getDbPath(username, file);
            if (relativePath == null) {
                return null;
            }
            String userId = userService.getUserIdByUserName(username);
            String oldEtag = etagDAO.findEtagByUserIdAndPathAndName(userId, relativePath, fileName);

            if (!ObjectUtil.equals(newEtag, oldEtag)) {
                etagDAO.setEtagByUserIdAndPathAndName(userId, relativePath, fileName, newEtag);
                log.debug("File ETag updated for {}: {} -> {}", relativePath + fileName, oldEtag, newEtag);

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
            boolean hasChildren = etagDAO.existsByUserIdAndPath(userId, currentFolderNormalizedPath);
            if (!hasChildren) {
                // 如果没有内容，则设置初始ETag
                String initialEtag = HashUtil.sha256(EMPTY_FOLDER_ETAG_BASE_STRING);
                etagDAO.setEtagByUserIdAndPathAndName(userId, relativePath, fileName, initialEtag);
                log.debug("Initial ETag set for new folder {}: {}", currentFolderNormalizedPath, initialEtag);
            }
            // 标记父文件夹需要更新ETag
            markFolderForEtagUpdate(userService.getUserIdByUserName(username), relativePath);
        } catch (DataAccessException e) {
            log.error("Database error setting initial ETag for folder {}: {}", relativePath, e.getMessage(), e);
        }
    }

    /**
     * 当文件或文件夹被删除时调用。
     * 异步标记其原父文件夹需要更新ETag。
     *
     * @param username 用户名
     * @param file     被删除的文件
     */
    public void handleItemDeletionAsync(String username, File file) {
        log.debug("Handling ETag update for parent of deleted item: {}", file.getAbsoluteFile());
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
            long count = etagDAO.countRootDirFilesWithoutEtag();
            if (count == 0) {
                run = false;
            }

            // 获取根目录下未处理etag的文件
            List<FileBaseEtagDTO> tasks = etagDAO.findFileBaseEtagDTOByRootDirFilesWithoutEtag();

            if (tasks.isEmpty()) {
                continue;
            }
            log.debug("Found {} files marked for ETag update. Processing...", tasks.size());
            for (FileBaseEtagDTO fileDoc : tasks) {
                String username = userService.getUserNameById(fileDoc.getUserId());
                Path path = Paths.get(fileProperties.getRootDir(), username, fileDoc.getPath(), fileDoc.getName());

                if (Files.exists(path)) {
                    updateFileEtagAndMarkParentAsync(username, path.toFile());
                }
            }
        }
    }

    /**
     * 实际处理标记为需要更新ETag的文件夹的循环。
     * 此方法由 ensureProcessingMarkedFolders 提交的任务在 EtagWorker 线程中执行。
     *
     * @param workerId 当前worker的唯一标识
     */
    private void processMarkedFoldersLoop(String workerId) {
        boolean run = true;
        while (run && !Thread.currentThread().isInterrupted()) {
            Sort sort = Sort.by(Sort.Direction.DESC, Constants.LAST_ETAG_UPDATE_REQUEST_AT_FIELD);
            List<FileBaseEtagDTO> tasks = etagDAO.findFileBaseEtagDTOByNeedUpdateFolder(sort);
            if (tasks.isEmpty()) {
                run = false;
                continue;
            }
            log.debug("[Worker {}] Found {} folders marked for ETag update. Processing...", workerId, tasks.size());

            for (FileBaseEtagDTO folderDoc : tasks) {
                if (Thread.currentThread().isInterrupted()) {
                    log.warn("[Worker {}] ETag processing loop was interrupted.", workerId);
                    run = false; // 尊重中断信号
                    break;
                }

                String folderPath = folderDoc.getPath();
                String docId = folderDoc.getId();
                String userId = folderDoc.getUserId();
                log.debug("[Worker {}] Processing ETag for folder: {} (ID: {})", workerId, folderPath, docId);

                try {
                    EtagCalculationResult result = calculateAndUpdateSingleFolderEtagInternal(folderDoc, workerId);

                    switch (result) {
                        case UPDATED:
                            // 成功更新，清除标记并标记父文件夹
                            etagDAO.clearMarkUpdateById(docId);
                            log.debug("[Worker {}] Cleared ETag update mark for folder: {}", workerId, folderPath + folderDoc.getName());
                            markFolderForEtagUpdate(userId, folderPath);
                            break;
                        case NOT_CHANGED:
                            // ETag未变，只需清除标记
                            etagDAO.clearMarkUpdateById(docId);
                            log.debug("[Worker {}] Cleared ETag update mark for folder: {} as ETag was unchanged.", workerId, folderPath + folderDoc.getName());
                            break;
                        case SKIPPED_CHILD_NULL:
                            // 跳过处理，不清除标记。增加一个小的延迟，避免立即重试导致CPU空转。
                            log.warn("[Worker {}] Folder {} processing skipped, will be retried later.", workerId, folderPath);
                            TimeUnit.MILLISECONDS.sleep(50);
                            break;
                        case ERROR:
                            // 出现预料之外的错误
                            handleProcessingError(docId, folderPath, new RuntimeException("Calculation returned ERROR state"), workerId);
                            break;
                    }
                } catch (Exception e) {
                    log.error("[Worker {}] Critical error processing ETag for folder {}: {}", workerId, folderPath, e.getMessage(), e);
                    handleProcessingError(docId, folderPath, e, workerId);
                }
            }
            log.debug("[Worker {}] Finished processing batch of {} folders.", workerId, tasks.size());
        }
        log.debug("[Worker {}] ETag processing loop finished or paused.", workerId);
    }

    /**
     * 标记指定文件夹需要ETag更新
     *
     * @param userId            用户ID
     * @param currentFolderPath 当前文件夹路径
     */
    private void markFolderForEtagUpdate(String userId, String currentFolderPath) {
        String parentFolderPath = getParentDbPath(currentFolderPath);
        if (parentFolderPath == null) {
            return;
        }
        String folderName = Paths.get(currentFolderPath).getFileName().toString();

        try {
            boolean modifiedOrMatch = etagDAO.setMarkUpdateByUserIdAndPathAndName(userId, parentFolderPath, folderName);
            if (modifiedOrMatch) {
                log.debug("Marked folder for ETag update: {}", currentFolderPath);
                ensureProcessingMarkedFolders();
            } else {
                log.debug("Folder not found to mark for ETag update: {}", currentFolderPath);
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
    private EtagCalculationResult calculateAndUpdateSingleFolderEtagInternal(FileBaseEtagDTO folderDoc, String workerId) {
        String folderPath = folderDoc.getPath();
        String oldEtag = folderDoc.getEtag();
        String userId = folderDoc.getUserId();
        String newCalculatedEtag;

        String currentFolderNormalizedPath = folderDoc.getPath() + folderDoc.getName() + "/";
        List<FileBaseEtagDTO> children = etagDAO.findFileBaseEtagDTOByUserIdAndPath(userId, currentFolderNormalizedPath);
        long folderSize = 0;
        if (children.isEmpty()) {
            newCalculatedEtag = HashUtil.sha256(EMPTY_FOLDER_ETAG_BASE_STRING);
        } else {
            List<String> childRepresentations = children.stream().sorted(Comparator.comparing(FileBaseEtagDTO::getName)) // 按名称排序
                    .map(child -> formatChildRepresentation(workerId, child)).toList();
            StringBuilder combinedRepresentation = new StringBuilder();
            for (String rep : childRepresentations) {
                if (rep == null) {
                    // 子项未准备好，返回SKIPPED状态
                    log.warn("[Worker {}] Skipped ETag calculation for folder {} because a child item's ETag was null.", workerId, folderPath);
                    return EtagCalculationResult.SKIPPED_CHILD_NULL;
                }
                if (CharSequenceUtil.isNotBlank(rep)) {
                    combinedRepresentation.append(rep).append(";");
                }
            }
            newCalculatedEtag = HashUtil.sha256(combinedRepresentation.toString());

            // 计算文件夹大小
            folderSize = getFolderSize(userId, currentFolderNormalizedPath);
        }

        if (!newCalculatedEtag.equals(oldEtag)) {
            // 更新文件夹大小(如果使用乐观锁，需要检查版本号)
            long modifiedCount = etagDAO.updateEtagAndSizeById(folderDoc.getId(), newCalculatedEtag, folderSize);
            if (modifiedCount > 0) {
                log.debug("[Worker {}] Folder ETag updated for {}: {} -> {}", workerId, folderPath + folderDoc.getName(), oldEtag, newCalculatedEtag);
                return EtagCalculationResult.UPDATED;
            } else {
                // 可能在计算和写入之间，该文档被其他worker处理了（如果ETag已更新为相同值）或版本冲突
                log.warn("[Worker {}] Folder ETag for {} was calculated as {} (old: {}), but DB update reported no modification. Possible race condition or already up-to-date.", workerId, folderPath, newCalculatedEtag, oldEtag);
                return EtagCalculationResult.NOT_CHANGED; // 没有实际修改数据库
            }
        }
        log.debug("[Worker {}] Folder ETag for {} did not change (calculated: {}, current: {}).", workerId, folderPath, newCalculatedEtag, oldEtag);
        return EtagCalculationResult.NOT_CHANGED;
    }

    private void handleProcessingError(String docId, String folderPath, Exception e, String workerId) {
        int attempts = etagDAO.findEtagUpdateFailedAttemptsById(docId);

        String errorMsg = e.getMessage().substring(0, Math.min(e.getMessage().length(), 1000)); // 限制错误信息长度
        Boolean needsEtagUpdate = null;
        if (attempts >= MAX_ETAG_UPDATE_ATTEMPTS) {
            needsEtagUpdate = false;
            log.error("[Worker {}] Folder {} ETag update failed after {} attempts. Giving up.", workerId, folderPath, attempts);
        } else {
            // 可以选择让它下次被重新拾取 (needsEtagUpdate 保持 true)
            log.warn("[Worker {}] Folder {} ETag update failed (attempt {}). Will retry.", workerId, folderPath, attempts);
        }
        etagDAO.setFailedEtagById(docId, attempts, errorMsg, needsEtagUpdate);
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

    private String formatChildRepresentation(String workerId, FileBaseEtagDTO child) {
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

    @PreDestroy
    public void cleanup() {
        if (executorMarkedFoldersService != null) {
            executorMarkedFoldersService.shutdown();
        }
    }

}
