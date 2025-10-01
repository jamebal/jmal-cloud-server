package com.jmal.clouddisk.config.hints;

import org.hibernate.bytecode.internal.none.BytecodeProviderImpl;
import org.springframework.aot.hint.*;

import java.io.IOException;
import java.util.Collections;

public class MyRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(CharSequence.class, MemberCategory.INVOKE_PUBLIC_METHODS);

        hints.reflection().registerType(IOException.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        hints.reflection().registerType(org.bson.Document.class, MemberCategory.DECLARED_FIELDS);
        hints.reflection().registerType(
                TypeReference.of(org.bson.types.ObjectId.class),
                hint -> hint
                        .withMembers(MemberCategory.DECLARED_FIELDS)
                        .withMethod("getDate", Collections.emptyList(), ExecutableMode.INVOKE)
                        .withMethod("getTimestamp", Collections.emptyList(), ExecutableMode.INVOKE)
        );
        hints.reflection().registerType(
                TypeReference.of("org.bson.types.ObjectId$SerializationProxy"),
                hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
        );

        hints.reflection().registerType(BytecodeProviderImpl.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS);
        hints.reflection().registerType(org.apache.coyote.AbstractProtocol.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS);
    }
}
