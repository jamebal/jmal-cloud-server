package com.jmal.clouddisk.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.jmal.clouddisk.controller.rest.sse.Message;
import com.jmal.clouddisk.controller.rest.sse.SseController;
import com.jmal.clouddisk.dao.ITrashDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ThrottleExecutor;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final SseController sseController;

    private final UserLoginHolder userLoginHolder;

    private final CommonUserService commonUserService;

    private final ITrashDAO trashDAO;

    private final Cache<String, Map<String, ThrottleExecutor>> throttleExecutorCache = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES) // 用户10分钟不活跃后，缓存自动过期
            .maximumSize(10000) // 最多缓存10000个用户的节流器
            .removalListener((String username, Map<String, ThrottleExecutor> map, RemovalCause cause) -> {
                // 2. 添加移除监听器，用于释放资源
                log.info("Removing throttle executors for user '{}' due to {}", username, cause);
                if (map != null) {
                    map.values().forEach(executor -> {
                        // 假设 ThrottleExecutor 实现了 shutdown 接口
                        if (executor != null) {
                            executor.shutdown();
                        }
                    });
                }
            })
            .build();

    private final Cache<String, Long> userSpaceCache = Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.SECONDS).build();

    public long takeUpSpace(String userId) throws CommonException {
        return occupiedSpace(userId);
    }

    public long occupiedSpace(String userId) {
        Long space = userSpaceCache.get(userId, _ -> calculateTotalOccupiedSpace(userId).blockingGet());
        if (space == null) {
            space = 0L;
        }
        ConsumerDO consumerDO = commonUserService.getUserInfoById(userId);
        if (consumerDO != null && consumerDO.getQuota() != null) {
            if (space >= consumerDO.getQuota() * 1024L * 1024L * 1024L) {
                // 空间已满
                CaffeineUtil.setSpaceFull(userId);
            } else {
                if (CaffeineUtil.spaceFull(userId)) {
                    CaffeineUtil.removeSpaceFull(userId);
                }
            }
        }
        return space;
    }

    public Single<Long> calculateTotalOccupiedSpace(String userId) {
        Single<Long> space1Single = getOccupiedSpaceAsync(userId, CommonFileService.COLLECTION_NAME);
        Single<Long> space2Single = getOccupiedSpaceAsync(userId, CommonFileService.TRASH_COLLECTION_NAME);
        return Single.zip(space1Single, space2Single, Long::sum);
    }

    public Single<Long> getOccupiedSpaceAsync(String userId, String collectionName) {
        return Single.fromCallable(() -> trashDAO.getOccupiedSpace(userId, collectionName)).subscribeOn(Schedulers.io()).doOnError(e -> log.error(e.getMessage(), e));
    }

    public void pushMessage(String username, Object message, String url) {
        Completable.fromAction(() -> pushMessageSync(username, message, url)).subscribeOn(Schedulers.io()).subscribe();
    }

    /**
     * 给用户推送消息
     *
     * @param username username
     * @param message  message
     * @param url      url
     */
    public void pushMessageSync(String username, Object message, String url) {
        if (CharSequenceUtil.isBlank(username)) {
            return;
        }
        if (timelyPush(username, message, url)) return;
        if (Constants.CREATE_FILE.equals(url) || Constants.DELETE_FILE.equals(url)) {
            Map<String, ThrottleExecutor> userExecutors = throttleExecutorCache.get(username, k -> new ConcurrentHashMap<>(8));
            ThrottleExecutor throttleExecutor = userExecutors.computeIfAbsent(url, key -> {
                log.debug("Creating new ThrottleExecutor for user '{}' and url '{}'", username, key);
                return new ThrottleExecutor(300); // 300ms 节流窗口
            });

            throttleExecutor.schedule(() -> pushMsg(username, message, url));
        } else {
            pushMsg(username, message, url);
        }
    }

    private boolean timelyPush(String username, Object message, String url) {
        if (Constants.CREATE_FILE.equals(url)) {
            if (message instanceof Document setDoc) {
                Object set = setDoc.get("$set");
                if (set instanceof Document doc) {
                    doc.remove("content");
                    Boolean isFolder = doc.getBoolean(Constants.IS_FOLDER);
                    if (Boolean.TRUE.equals(isFolder)) {
                        pushMsg(username, message, url);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void pushMsg(String username, Object message, String url) {
        Message msg = new Message();
        String userId = userLoginHolder.getUserId();
        if (CharSequenceUtil.isBlank(userId)) {
            userId = commonUserService.getUserIdByUserName(username);
        }
        if (!CharSequenceUtil.isBlank(userId)) {
            long takeUpSpace = occupiedSpace(userId);
            msg.setSpace(takeUpSpace);
        }
        if (message == null) {
            message = new Document();
        }
        if (message instanceof FileDocument fileDocument) {
            fileDocument.setId(null);
            fileDocument.setContent(null);
            fileDocument.setMusic(null);
            fileDocument.setContentText(null);
        }
        msg.setUrl(url);
        msg.setUsername(username);
        msg.setBody(message);
        sseController.sendEvent(msg);
    }

}
