package com.jmal.clouddisk.dao.impl.jpa.write.consumer;

import com.jmal.clouddisk.dao.impl.jpa.repository.UserRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserOperationHandler implements IDataOperationHandler<IUserOperation> {

    private final UserRepository userRepository;

    @Override
    public void handle(IUserOperation operation) {
        switch (operation) {
            case UserOperation.CreateAll createOp -> userRepository.saveAll(createOp.entities());
        }
    }

}
