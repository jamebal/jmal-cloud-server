package com.jmal.clouddisk.dao.impl.jpa.write.tag;

import com.jmal.clouddisk.dao.impl.jpa.repository.TagRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TagOperationHandler implements IDataOperationHandler<ITagOperation> {

    private final TagRepository tagRepository;

    @Override
    public void handle(ITagOperation operation) {
        switch (operation) {
            case TagOperation.CreateAll createOp -> tagRepository.saveAll(createOp.entities());
        }
    }

}
