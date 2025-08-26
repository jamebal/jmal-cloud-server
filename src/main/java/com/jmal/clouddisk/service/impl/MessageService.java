package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.text.CharSequenceUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jmal.clouddisk.controller.rest.sse.Message;
import com.jmal.clouddisk.controller.rest.sse.SseController;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ThrottleExecutor;
import com.mongodb.client.AggregateIterable;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonNull;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final SseController sseController;

    private final UserLoginHolder userLoginHolder;

    private final CommonUserService commonUserService;

    private final MongoTemplate mongoTemplate;

    private final Cache<String, Map<String, ThrottleExecutor>> throttleExecutorCache = Caffeine.newBuilder().build();

    private final Cache<String, Long> userSpaceCache = Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.SECONDS).build();

    public long takeUpSpace(String userId) throws CommonException {
        return occupiedSpace(userId);
    }

    public long occupiedSpace(String userId) {
        Long space = userSpaceCache.get(userId, key -> calculateTotalOccupiedSpace(userId).blockingGet());
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
        return Single.fromCallable(() -> getOccupiedSpace(userId, collectionName)).subscribeOn(Schedulers.io()).doOnError(e -> log.error(e.getMessage(), e));
    }

    public long getOccupiedSpace(String userId, String collectionName) {
        Long space = 0L;
        List<Bson> list = Arrays.asList(match(and(eq(IUserService.USER_ID, userId), eq(Constants.IS_FOLDER, false))), group(new BsonNull(), sum(Constants.TOTAL_SIZE, "$size")));
        AggregateIterable<Document> aggregateIterable = mongoTemplate.getCollection(collectionName).aggregate(list);
        Document doc = aggregateIterable.first();
        if (doc != null) {
            space = Convert.toLong(doc.get(Constants.TOTAL_SIZE), 0L);
        }
        return space;
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
        if (timelyPush(username, message, url)) return;
        if (Constants.CREATE_FILE.equals(url) || Constants.DELETE_FILE.equals(url)) {
            Map<String, ThrottleExecutor> throttleExecutorMap = throttleExecutorCache.get(username, key -> new HashMap<>(8));
            if (throttleExecutorMap != null) {
                ThrottleExecutor throttleExecutor = throttleExecutorMap.get(url);
                if (throttleExecutor == null) {
                    throttleExecutor = new ThrottleExecutor(300);
                    throttleExecutorMap.put(url, throttleExecutor);
                }
                throttleExecutor.schedule(() -> pushMsg(username, message, url));
            }
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
            fileDocument.setContent(null);
            fileDocument.setMusic(null);
            fileDocument.setContentText(null);
        }
        msg.setUrl(url);
        msg.setUsername(username);
        msg.setBody(message);
        sseController.sendEvent(msg);
    }

    public void notifyCreateIndexQueue(String fileId) {

    }

}
