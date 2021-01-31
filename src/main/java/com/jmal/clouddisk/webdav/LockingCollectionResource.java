package com.jmal.clouddisk.webdav;

import io.milton.http.LockInfo;
import io.milton.http.LockTimeout;
import io.milton.http.LockToken;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.CollectionResource;

public interface  LockingCollectionResource extends CollectionResource, LockableResource {
     
    /**
     * Create an empty non-collection resource of the given name and immediately
     * lock it.
     * <P/>
     * It is suggested that the implementor have a specific Resource class to act
     * as the lock null resource. You should consider using the {@see LockNullResource}
     * interface.
     *
     * @see  LockNullResource
     *
     * @param name - the name of the resource to create
     * @param timeout - in seconds
     * @param lockInfo
     * @return
     */
    public LockToken createAndLock(String name, LockTimeout timeout, LockInfo lockInfo) throws NotAuthorizedException;
     
}
