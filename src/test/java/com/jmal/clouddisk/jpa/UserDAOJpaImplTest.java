package com.jmal.clouddisk.jpa;

import com.jmal.clouddisk.dao.impl.jpa.UserDAOJpaImpl;
import com.jmal.clouddisk.dao.impl.jpa.repository.UserRepository;
import com.jmal.clouddisk.dao.util.MyQuery;
import com.jmal.clouddisk.dao.util.MyUpdate;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class UserDAOJpaImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDAOJpaImpl userJpa;

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
        when(userRepository.save(any(ConsumerDO.class))).thenReturn(testUser);

        // 调用被测试方法
        ConsumerDO result = userJpa.save(testUser);

        // 验证
        assertNotNull(result);
        assertEquals("user-123", result.getId());
        assertEquals("testuser", result.getUsername());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void testFindAllById() {
        // 设置模拟行为
        when(userRepository.findAllById(anyList())).thenReturn(testUsers);

        // 调用被测试方法
        List<ConsumerDO> result = userJpa.findAllById(testIds);

        // 验证
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("user-123", result.get(0).getId());
        assertEquals("user-456", result.get(1).getId());
        verify(userRepository, times(1)).findAllById(testIds);
    }

    @Test
    void testDeleteAllById() {
        // 调用被测试方法
        userJpa.deleteAllById(testIds);

        // 验证
        verify(userRepository, times(1)).deleteAllById(testIds);
    }

    @Test
    void testFindById() {
        // 设置模拟行为
        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(userRepository.findById("non-existent")).thenReturn(Optional.empty());

        // 调用被测试方法 - 存在的用户
        ConsumerDO result1 = userJpa.findById("user-123");

        // 验证
        assertNotNull(result1);
        assertEquals("user-123", result1.getId());

        // 调用被测试方法 - 不存在的用户
        ConsumerDO result2 = userJpa.findById("non-existent");

        // 验证
        assertNull(result2);
        verify(userRepository, times(1)).findById("user-123");
        verify(userRepository, times(1)).findById("non-existent");
    }

    @Test
    void testFindByUsername() {
        // 设置模拟行为
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userRepository.findByUsername("non-existent")).thenReturn(Optional.empty());

        // 调用被测试方法 - 存在的用户
        ConsumerDO result1 = userJpa.findByUsername("testuser");

        // 验证
        assertNotNull(result1);
        assertEquals("testuser", result1.getUsername());

        // 调用被测试方法 - 不存在的用户
        ConsumerDO result2 = userJpa.findByUsername("non-existent");

        // 验证
        assertNull(result2);
        verify(userRepository, times(1)).findByUsername("testuser");
        verify(userRepository, times(1)).findByUsername("non-existent");
    }

    @Test
    void testFindOneByCreatorTrue() {
        // 设置模拟行为
        when(userRepository.findOneByCreatorTrue()).thenReturn(Optional.of(testUser));

        // 调用被测试方法
        ConsumerDO result = userJpa.findOneByCreatorTrue();

        // 验证
        assertNotNull(result);
        assertTrue(result.getCreator());
        verify(userRepository, times(1)).findOneByCreatorTrue();
    }

    @Test
    void testUpsert_Existing() {
        // 准备测试数据
        MyQuery query = new MyQuery();
        query.eq("username", "testuser");

        MyUpdate update = new MyUpdate();
        update.set("showName", "Updated User");
        update.set("quota", 2000);

        // 设置模拟行为 - 找到已存在的用户
        when(userRepository.findOne(any(Specification.class))).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(ConsumerDO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 调用被测试方法
        userJpa.upsert(query, update);

        // 验证
        verify(userRepository, times(1)).findOne(any(Specification.class));
        verify(userRepository, times(1)).save(argThat(consumer ->
            "Updated User".equals(consumer.getShowName()) &&
            2000 == consumer.getQuota()
        ));
    }

    @Test
    void testUpsert_New() {
        // 准备测试数据
        MyQuery query = new MyQuery();
        query.eq("username", "newuser");

        MyUpdate update = new MyUpdate();
        update.set("showName", "NewUser");
        update.set("password", "newpassword");

        // 设置模拟行为 - 没有找到用户，需要创建新用户
        when(userRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());
        when(userRepository.save(any(ConsumerDO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 调用被测试方法
        userJpa.upsert(query, update);

        // 验证
        verify(userRepository, times(1)).findOne(any(Specification.class));
        verify(userRepository, times(1)).save(argThat(consumer ->
            "NewUser".equals(consumer.getShowName()) &&
            "newpassword".equals(consumer.getPassword()) &&
            "newuser".equals(consumer.getUsername())
        ));
    }

    @Test
    void testApplyUpdateToEntity() {
        // 准备测试数据
        ConsumerDO entity = new ConsumerDO();
        MyUpdate update = new MyUpdate();
        update.set("username", "updateduser");
        update.set("password", "updatedpass");
        update.set("quota", 3000);
        update.set("mfaEnabled", true);
        update.set("roles", Arrays.asList("USER", "EDITOR"));
        update.set("nonExistingField", "value"); // 测试不存在的字段

        // 调用被测试方法
        userJpa.applyUpdateToEntity(entity, update);

        // 验证
        assertEquals("updateduser", entity.getUsername());
        assertEquals("updatedpass", entity.getPassword());
        assertEquals(3000, entity.getQuota());
        assertTrue(entity.getMfaEnabled());
        assertEquals(Arrays.asList("USER", "EDITOR"), entity.getRoles());
    }

    @Test
    void testApplyQueryToEntity() {
        // 准备测试数据
        ConsumerDO entity = new ConsumerDO();
        MyQuery query = new MyQuery();
        query.eq("username", "queryuser");
        query.eq("id", "query-id");
        query.eq("nonExistingField", "value"); // 测试不存在的字段

        // 调用被测试方法
        userJpa.applyQueryToEntity(entity, query);

        // 验证
        assertEquals("queryuser", entity.getUsername());
        assertEquals("query-id", entity.getId());
    }

    @Test
    void testSetConsumerField_Unset() {
        // 准备测试数据
        ConsumerDO entity = new ConsumerDO();
        entity.setUsername("oldusername");
        entity.setQuota(1000);

        MyUpdate update = new MyUpdate();
        update.unset("username");
        update.unset("quota");

        // 调用被测试方法
        userJpa.applyUpdateToEntity(entity, update);

        // 验证
        assertNull(entity.getUsername());
        assertNull(entity.getQuota());
    }
}
