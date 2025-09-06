package com.jmal.clouddisk.dao.impl.jpa.write.access_token;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.AccessTokenRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class AccessTokenOperationHandler implements IDataOperationHandler<IAccessTokenOperation> {

    private final AccessTokenRepository accessTokenRepository;

    @Override
    public void handle(IAccessTokenOperation operation) {
        switch (operation) {
            case AccessTokenOperation.CreateAll createAll -> accessTokenRepository.saveAll(createAll.entities());
            case AccessTokenOperation.Create create -> accessTokenRepository.save(create.entity());
            case AccessTokenOperation.DeleteByUsernameIn deleteByUsernameIn -> accessTokenRepository.deleteByUsernameIn(deleteByUsernameIn.usernames());
            case AccessTokenOperation.UpdateLastActiveTimeByUsernameAndToken update -> accessTokenRepository.updateLastActiveTimeByUsernameAndToken(update.username(), update.token(), update.lastActiveTime());
            case AccessTokenOperation.DeleteById deleteById -> accessTokenRepository.deleteById(deleteById.id());
        }
    }

}
