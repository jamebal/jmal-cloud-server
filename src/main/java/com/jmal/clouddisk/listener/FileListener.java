package com.jmal.clouddisk.listener;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.service.IFileService;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryChangeListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileListener implements DirectoryChangeListener {

    private final FileProperties fileProperties;

    private final IFileService fileService;

    /**
     * 续要过滤掉的目录列表
     */
    private final Set<Path> FILTER_DIR_SET = new CopyOnWriteArraySet<>();

    public void addFilterDir(Path path) {
        FILTER_DIR_SET.add(path);
    }

    public Set<Path> getFilterDirSet() {
        return FILTER_DIR_SET;
    }

    public void removeFilterDir(Path path) {
        FILTER_DIR_SET.remove(path);
    }

    public boolean containsFilterDir(Path path) {
        return FILTER_DIR_SET.contains(path);
    }

    @Override
    public void onEvent(DirectoryChangeEvent directoryChangeEvent) {
        Path eventPath = directoryChangeEvent.path();
        DirectoryChangeEvent.EventType eventType = directoryChangeEvent.eventType();
        // 检查是否为忽略路径
        if (FILTER_DIR_SET.stream().anyMatch(eventPath::startsWith)) {
            log.debug("Ignore Event: {}, Path: {}", eventType, eventPath);
            return;
        }
        log.debug("DirectoryChangeEvent: {}, Path: {}", eventType, eventPath);
        switch (eventType) {
            case CREATE:
                onFileCreate(eventPath.toFile());
                break;
            case MODIFY:
                onFileChange(eventPath.toFile());
                break;
            case DELETE:
                onFileDelete(eventPath.toFile());
                break;
        }
    }

    /**
     * 文件创建执行
     * @param file 文件
     */
    public void onFileCreate(File file) {
        try {
            // 判断文件名是否在monitorIgnoreFilePrefix中
            if (fileProperties.getMonitorIgnoreFilePrefix().stream().anyMatch(file.getName()::startsWith)) {
                log.info("忽略文件:{}", file.getAbsolutePath());
                return;
            }
            String username = ownerOfChangeFile(file);
            if (CharSequenceUtil.isBlank(username)) {
                return;
            }
            fileService.createFile(username, file);
            log.info("用户:{},新建文件:{}", username, file.getAbsolutePath());
        } catch (Exception e) {
            log.error("新建文件后续操作失败, {}", file.getAbsolutePath(), e);
        }
    }

    /**
     * 文件创建修改
     * @param file 文件
     */
    public void onFileChange(File file) {
        try {
            String username = ownerOfChangeFile(file);
            if (CharSequenceUtil.isBlank(username)) {
                return;
            }
            fileService.updateFile(username, file);
            log.info("用户:{},修改文件:{}", username, file.getAbsolutePath());
        } catch (Exception e) {
            log.error("修改文件后续操作失败", e);
        }
    }

    /**
     * 文件删除
     * @param file 文件
     */
    public void onFileDelete(File file) {
        try {
            String username = ownerOfChangeFile(file);
            if (CharSequenceUtil.isBlank(username)) {
                return;
            }
            fileService.deleteFile(username, file);
            log.info("用户:{},删除文件:{}", username, file.getAbsolutePath());
        } catch (Exception e) {
            log.error("删除文件后续操作失败", e);
        }
    }

    /**
     * 判断变化的文件属于哪个用户
     * @return username
     */
    private String ownerOfChangeFile(File file) {
        String username = null;
        try {
            int rootPathCount = Paths.get(fileProperties.getRootDir()).getNameCount();
            if (file.toPath().getNameCount() == rootPathCount + 1) {
                return null;
            }
            username = file.toPath().subpath(rootPathCount, rootPathCount + 1).toString();
        } catch (Exception e) {
            log.error("解析路径失败,fileAbsolutePath:{}", file.getAbsolutePath(), e);
        }
        return username;
    }

    @Override
    public boolean isWatching() {
        return DirectoryChangeListener.super.isWatching();
    }

    @Override
    public void onIdle(int i) {
        DirectoryChangeListener.super.onIdle(i);
    }

    @Override
    public void onException(Exception e) {
        DirectoryChangeListener.super.onException(e);
    }
}
