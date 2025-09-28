package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.dao.IOfficeConfigDAO;
import com.jmal.clouddisk.dao.write.IWriteService;
import com.jmal.clouddisk.dao.write.officeconfig.OfficeConfigOperation;
import com.jmal.clouddisk.dao.repository.jpa.OfficeConfigRepository;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.office.model.OfficeConfigDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class OfficeConfigDAOJpaImpl implements IOfficeConfigDAO {

    private final OfficeConfigRepository officeConfigRepository;
    private final IWriteService writeService;

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
