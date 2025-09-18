package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IOfficeConfigDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.OfficeConfigRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.officeconfig.OfficeConfigOperation;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.office.model.OfficeConfigDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class OfficeConfigDAOJpaImpl implements IOfficeConfigDAO, IWriteCommon<OfficeConfigDO> {

    private final IWriteService writeService;

    private final OfficeConfigRepository officeConfigRepository;

    @Override
    public void AsyncSaveAll(Iterable<OfficeConfigDO> entities) {
        try {
            writeService.submit(new OfficeConfigOperation.CreateAll(entities)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void upsert(OfficeConfigDO officeConfigDO) {
        try {
            writeService.submit(new OfficeConfigOperation.Upsert(officeConfigDO)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public OfficeConfigDO findOne() {
        return officeConfigRepository.findAll().stream().findFirst().orElse(null);
    }
}
