package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IHeartwingsDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.HeartwingsRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.setting.WebSiteSettingOperation;
import com.jmal.clouddisk.model.HeartwingsDO;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class HeartwingsDAOJpaImpl implements IHeartwingsDAO {

    private final HeartwingsRepository heartwingsRepository;

    private final IWriteService writeService;

    @Override
    public void save(HeartwingsDO heartwingsDO) {
        log.debug("保存心语: username={}, heartwings={}",
                 heartwingsDO.getUsername(),
                 heartwingsDO.getHeartwings());

        try {
            // 如果是新记录，设置创建时间
            if (heartwingsDO.getId() == null || heartwingsDO.getId().trim().isEmpty()) {
                heartwingsDO.setCreateTime(LocalDateTime.now());
            }

            validateHeartwingsDO(heartwingsDO);

            writeService.submit(new WebSiteSettingOperation.CreateHeartwings(heartwingsDO));

        } catch (Exception e) {
            log.error("保存心语失败: username={}, heartwings={}, error={}",
                     heartwingsDO.getUsername(),
                     heartwingsDO.getHeartwings(),
                     e.getMessage(), e);
            throw new RuntimeException("保存心语失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ResponseResult<List<HeartwingsDO>> getWebsiteHeartwings(Integer page, Integer pageSize, String order) {
        log.debug("查询网站心语列表: page={}, pageSize={}, order={}", page, pageSize, order);

        try {
            // 参数验证和默认值设置
            int pageNum = (page != null && page > 0) ? page - 1 : 0; // JPA页码从0开始
            int size = (pageSize != null && pageSize > 0) ? pageSize : 10;
            String sortOrder = (order != null && !order.trim().isEmpty()) ? order.trim().toLowerCase() : "desc";

            // 创建排序对象
            Sort sort = createSort(sortOrder);

            // 创建分页对象
            Pageable pageable = PageRequest.of(pageNum, size, sort);

            // 执行查询
            Page<HeartwingsDO> heartwings = heartwingsRepository.findAll(pageable);

            return ResultUtil.success(heartwings.getContent()).setCount(heartwingsRepository.count());

        } catch (Exception e) {
            log.error("查询网站心语列表失败: page={}, pageSize={}, order={}, error={}",
                     page, pageSize, order, e.getMessage(), e);
            ResponseResult<List<HeartwingsDO>> errorResult = new ResponseResult<>();
            errorResult.setMessage("查询失败: " + e.getMessage());
            errorResult.setData(List.of());
            return errorResult;
        }
    }

    /**
     * 创建排序对象
     */
    private Sort createSort(String order) {
        Sort.Direction direction = "asc".equals(order) ? Sort.Direction.ASC : Sort.Direction.DESC;

        // 默认按创建时间排序
        return Sort.by(direction, "createTime")
                   .and(Sort.by(Sort.Direction.DESC, "id")); // 二级排序，确保结果稳定
    }

    /**
     * 验证心语数据
     */
    private void validateHeartwingsDO(HeartwingsDO heartwingsDO) {
        if (heartwingsDO == null) {
            throw new IllegalArgumentException("心语对象不能为空");
        }

        if (heartwingsDO.getHeartwings() == null || heartwingsDO.getHeartwings().trim().isEmpty()) {
            throw new IllegalArgumentException("心语内容不能为空");
        }

        if (heartwingsDO.getUsername() == null || heartwingsDO.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }

        if (heartwingsDO.getCreator() == null || heartwingsDO.getCreator().trim().isEmpty()) {
            throw new IllegalArgumentException("创建者ID不能为空");
        }

        // 心语内容长度验证
        if (heartwingsDO.getHeartwings().length() > 1000) {
            throw new IllegalArgumentException("心语内容长度不能超过1000个字符");
        }
    }

}
