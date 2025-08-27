package com.jmal.clouddisk.media.mongodb;

import com.jmal.clouddisk.dao.impl.mongodb.UserImpl;
import com.jmal.clouddisk.dao.util.MyQuery;
import com.jmal.clouddisk.dao.util.MyUpdate;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private UserImpl userImpl;

    @Captor
    private ArgumentCaptor<Query> queryCaptor;

    @Captor
    private ArgumentCaptor<Update> updateCaptor;

    private ConsumerDO testUser;
    private List<ConsumerDO> testUsers;
    private List<String> testIds;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        testUser = new ConsumerDO();
        testUser.setId("user-123");
        testUser.setUsername("testuser");
        testUser.setPassword("password123");
        testUser.setShowName("Test User");
        testUser.setCreatedTime(LocalDateTime.now());
        testUser.setRoles(Arrays.asList("USER", "ADMIN"));
        testUser.setQuota(1000);
        testUser.setTakeUpSpace(500L);
        testUser.setCreator(true);

        ConsumerDO testUser2 = new ConsumerDO();
        testUser2.setId("user-456");
        testUser2.setUsername("testuser2");

        testUsers = Arrays.asList(testUser, testUser2);
        testIds = Arrays.asList("user-123", "user-456");
    }

    @Test
    void testSave() {
        // 设置模拟行为
        when(mongoTemplate.save(any(ConsumerDO.class))).thenReturn(testUser);

        // 调用被测试方法
        ConsumerDO result = userImpl.save(testUser);

        // 验证
        assertNotNull(result);
        assertEquals("user-123", result.getId());
        assertEquals("testuser", result.getUsername());
        verify(mongoTemplate, times(1)).save(testUser);
    }

    @Test
    void testFindAllById() {
        // 设置模拟行为
        when(mongoTemplate.find(any(Query.class), eq(ConsumerDO.class))).thenReturn(testUsers);

        // 调用被测试方法
        List<ConsumerDO> result = userImpl.findAllById(testIds);

        // 验证
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("user-123", result.get(0).getId());
        assertEquals("user-456", result.get(1).getId());

        // 验证传递给 mongoTemplate 的查询条件
        verify(mongoTemplate).find(queryCaptor.capture(), eq(ConsumerDO.class));
        Query capturedQuery = queryCaptor.getValue();
        assertTrue(capturedQuery.toString().contains("{ \"_id\" : { \"$in\" : [\"user-123\", \"user-456\"]}}"));
    }

    @Test
    void testDeleteAllById() {
        // 调用被测试方法
        userImpl.deleteAllById(testIds);

        // 验证传递给 mongoTemplate 的查询条件
        verify(mongoTemplate).remove(queryCaptor.capture(), eq(ConsumerDO.class));
        Query capturedQuery = queryCaptor.getValue();
        assertTrue(capturedQuery.toString().contains("{ \"_id\" : { \"$in\" : [\"user-123\", \"user-456\"]}}"));
    }

    @Test
    void testFindById() {
        // 设置模拟行为
        when(mongoTemplate.findById("user-123", ConsumerDO.class)).thenReturn(testUser);
        when(mongoTemplate.findById("non-existent", ConsumerDO.class)).thenReturn(null);

        // 调用被测试方法 - 存在的用户
        ConsumerDO result1 = userImpl.findById("user-123");

        // 验证
        assertNotNull(result1);
        assertEquals("user-123", result1.getId());

        // 调用被测试方法 - 不存在的用户
        ConsumerDO result2 = userImpl.findById("non-existent");

        // 验证
        assertNull(result2);
        verify(mongoTemplate, times(1)).findById("user-123", ConsumerDO.class);
        verify(mongoTemplate, times(1)).findById("non-existent", ConsumerDO.class);
    }

    @Test
    void testUpsert() {
        // 准备测试数据
        MyQuery myQuery = new MyQuery();
        myQuery.eq("username", "testuser");

        MyUpdate myUpdate = new MyUpdate();
        myUpdate.set("showName", "Updated Name");
        myUpdate.set("quota", 2000);

        // 调用被测试方法
        userImpl.upsert(myQuery, myUpdate);

        // 验证传递给 mongoTemplate 的参数
        verify(mongoTemplate).upsert(queryCaptor.capture(), updateCaptor.capture(), eq(ConsumerDO.class));

        Query capturedQuery = queryCaptor.getValue();
        Update capturedUpdate = updateCaptor.getValue();

        // 这里的验证可能需要根据你的 MongoQueryUtil 实际行为进行调整
        assertNotNull(capturedQuery);
        assertNotNull(capturedUpdate);
    }

    @Test
    void testFindByUsername() {
        // 设置模拟行为
        when(mongoTemplate.findOne(any(Query.class), eq(ConsumerDO.class))).thenReturn(testUser);

        // 调用被测试方法
        ConsumerDO result = userImpl.findByUsername("testuser");

        // 验证
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());

        // 验证传递给 mongoTemplate 的查询条件
        verify(mongoTemplate).findOne(queryCaptor.capture(), eq(ConsumerDO.class));
        Query capturedQuery = queryCaptor.getValue();
        assertTrue(capturedQuery.toString().contains(IUserService.USERNAME));
        assertTrue(capturedQuery.toString().contains("testuser"));
    }

    @Test
    void testFindOneByCreatorTrue() {
        // 设置模拟行为
        when(mongoTemplate.findOne(any(Query.class), eq(ConsumerDO.class))).thenReturn(testUser);

        // 调用被测试方法
        ConsumerDO result = userImpl.findOneByCreatorTrue();

        // 验证
        assertNotNull(result);
        assertTrue(result.getCreator());

        // 验证传递给 mongoTemplate 的查询条件
        verify(mongoTemplate).findOne(queryCaptor.capture(), eq(ConsumerDO.class));
        Query capturedQuery = queryCaptor.getValue();
        assertTrue(capturedQuery.toString().contains("\"creator\" : true"));
    }

    @Test
    void testFindByUsername_NotFound() {
        // 设置模拟行为 - 没有找到用户
        when(mongoTemplate.findOne(any(Query.class), eq(ConsumerDO.class))).thenReturn(null);

        // 调用被测试方法
        ConsumerDO result = userImpl.findByUsername("nonexistent");

        // 验证
        assertNull(result);
        verify(mongoTemplate).findOne(any(Query.class), eq(ConsumerDO.class));
    }

    @Test
    void testFindOneByCreatorTrue_NotFound() {
        // 设置模拟行为 - 没有找到创建者
        when(mongoTemplate.findOne(any(Query.class), eq(ConsumerDO.class))).thenReturn(null);

        // 调用被测试方法
        ConsumerDO result = userImpl.findOneByCreatorTrue();

        // 验证
        assertNull(result);
        verify(mongoTemplate).findOne(any(Query.class), eq(ConsumerDO.class));
    }
}
