package com.jmal.clouddisk.dao.impl.jpa.write.stunchannel;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IStunChannelOperation<R> extends IDataOperation<R>
        permits StunChannelOperation.CreateAll, StunChannelOperation.Upsert {
}
