package com.jmal.clouddisk.dao.impl.jpa.write.etag;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileEtagRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("etagSetFoldersWithoutEtagHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class SetFoldersWithoutEtagHandler implements IDataOperationHandler<EtagOperation.SetFoldersWithoutEtag, Integer> {

    private final FileEtagRepository repo;

    @Override
    public Integer handle(EtagOperation.SetFoldersWithoutEtag op) {
        return repo.setFoldersWithoutEtag();
    }
}
