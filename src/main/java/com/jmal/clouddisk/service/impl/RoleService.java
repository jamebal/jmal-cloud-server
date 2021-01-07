package com.jmal.clouddisk.service.impl;

import cn.hutool.extra.cglib.CglibUtil;
import com.jmal.clouddisk.model.rbac.RoleDO;
import com.jmal.clouddisk.model.rbac.RoleDTO;
import com.jmal.clouddisk.util.MongoUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description 角色管理
 * @blame jmal
 * @Date 2021/1/7 7:45 下午
 */
@Service
public class RoleService {

    public static final String COLLECTION_NAME = "role";

    @Autowired
    private MongoTemplate mongoTemplate;

    /***
     * 角色列表
     * @param page
     * @param pageSize
     * @return
     */
    public List<RoleDO> list(Integer page, Integer pageSize) {
        int skip = (page - 1) * pageSize;
        Query query = new Query();
        query.skip(skip);
        query.limit(pageSize);
        return mongoTemplate.find(query, RoleDO.class, COLLECTION_NAME);
    }

    /***
     * roleCode是否存在
     * @param roleCode
     * @return
     */
    private boolean existsRoleCode(String roleCode){
        Query query = new Query();
        query.addCriteria(Criteria.where("code").is(roleCode));
        return mongoTemplate.exists(query, COLLECTION_NAME);
    }

    /***
     * roleId是否存在
     * @param roleId
     * @return
     */
    private boolean existsRoleId(String roleId){
        Query query = new Query();
        query.addCriteria(Criteria.where("roleId").is(roleId));
        return mongoTemplate.exists(query, COLLECTION_NAME);
    }

    /***
     * 添加角色
     * @param roleDTO
     * @return
     */
    public ResponseResult<Object> add(RoleDTO roleDTO) {
        if (existsRoleCode(roleDTO.getCode())) {
            return ResultUtil.warning("该角色标识已存在");
        }
        RoleDO roleDO = new RoleDO();
        CglibUtil.copy(roleDTO, roleDO);
        LocalDateTime dateNow = LocalDateTime.now();
        roleDO.setCreateTime(dateNow);
        roleDO.setUpdateTime(dateNow);
        mongoTemplate.save(roleDO);
        return ResultUtil.success();
    }

    /***
     * 更新角色
     * @param roleDTO
     * @return
     */
    public ResponseResult<Object> update(RoleDTO roleDTO) {
        if (existsRoleId(roleDTO.getId())) {
            return ResultUtil.warning("不存在的角色Id");
        }
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("_id").nin(roleDTO.getId()));
        query1.addCriteria(Criteria.where("code").is(roleDTO.getCode()));
        if(mongoTemplate.exists(query1, COLLECTION_NAME)){
            return ResultUtil.warning("该分类标识已存在");
        }
        RoleDO roleDO = new RoleDO();
        CglibUtil.copy(roleDTO, roleDO);
        LocalDateTime dateNow = LocalDateTime.now();
        roleDO.setUpdateTime(dateNow);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(roleDO.getId()));
        Update update = MongoUtil.getUpdate(roleDO);
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 删除角色
     * @param roleIdList
     */
    public void delete(List<String> roleIdList) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(roleIdList));
        mongoTemplate.remove(query, COLLECTION_NAME);
    }
}
