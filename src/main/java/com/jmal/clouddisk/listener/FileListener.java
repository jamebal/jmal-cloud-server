package com.jmal.clouddisk.listener;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.service.IFileService;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryChangeListener;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileListener implements DirectoryChangeListener {

    private final FileProperties fileProperties;
    private final IFileService fileService;

    /**
     * 需要过滤掉的目录列表
     */
    @Getter
    private final Set<Path> filterDirSet = new CopyOnWriteArraySet<>();

    private final FlowableProcessor<DirectoryChangeEvent> eventProcessor = PublishProcessor.<DirectoryChangeEvent>create().toSerialized();
    private Disposable eventSubscription;

    // 使用 ConcurrentHashMap 来存储每个文件的最新事件
    private final ConcurrentHashMap<Path, DirectoryChangeEvent.EventType> lastFileEvents = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        eventSubscription = eventProcessor
                .groupBy(DirectoryChangeEvent::path) // 根据文件路径分组
                .flatMap(groupedFlowable -> groupedFlowable
                        .debounce(100, TimeUnit.MILLISECONDS) // 对每个文件路径的事件进行 Debounce
                )
                .subscribeOn(Schedulers.io()) // 在 IO 线程池中执行处理
                .subscribe(this::processEvent, throwable -> log.error("Error processing file event", throwable));
    }

    public void addFilterDir(Path path) {
        filterDirSet.add(path);
    }

    public void removeFilterDir(Path path) {
        filterDirSet.remove(path);
    }

    public boolean containsFilterDir(Path path) {
        return filterDirSet.contains(path);
    }

    @Override
    public void onEvent(DirectoryChangeEvent directoryChangeEvent) {
        Path eventPath = directoryChangeEvent.path();
        DirectoryChangeEvent.EventType eventType = directoryChangeEvent.eventType();

        // 检查是否为忽略路径
        if (filterDirSet.stream().anyMatch(eventPath::startsWith)) {
            log.debug("Ignore Event: {}, Path: {}", eventType, eventPath);
            return;
        }

        if (fileProperties.getMonitorIgnoreFilePrefix().stream().anyMatch(eventPath.getFileName()::startsWith)) {
            log.debug("忽略文件:{}", eventPath.toFile().getAbsolutePath());
            return;
        }

        log.debug("Received Event: {}, Path: {}", eventType, eventPath);
        eventProcessor.onNext(directoryChangeEvent);
    }

    private void processEvent(DirectoryChangeEvent directoryChangeEvent) {
        Path eventPath = directoryChangeEvent.path();
        DirectoryChangeEvent.EventType eventType = directoryChangeEvent.eventType();
        File file = eventPath.toFile();
        switch (eventType) {
            case CREATE:
                onFileCreate(file);
                break;
            case MODIFY:
                onFileChange(file);
                break;
            case DELETE:
                onFileDelete(file);
                break;
            default:
                break;
        }
    }

    /**
     * 文件创建执行
     *
     * @param file 文件
     */
    public void onFileCreate(File file) {
        try {
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
     *
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
            log.error("修改文件后续操作失败, fileAbsolutePath: {}", file.getAbsolutePath(), e);
        }
    }

    /**
     * 文件删除
     *
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
            log.error("删除文件后续操作失败, fileAbsolutePath: {}", file.getAbsolutePath(), e);
        }
    }

    /**
     * 判断变化的文件属于哪个用户
     *
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
            log.error("解析路径失败, fileAbsolutePath: {}", file.getAbsolutePath(), e);
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

    @PreDestroy
    public void shutdown() {
        if (eventSubscription != null && !eventSubscription.isDisposed()) {
            eventSubscription.dispose();
        }
    }
}
