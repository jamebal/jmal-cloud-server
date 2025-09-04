package com.jmal.clouddisk.config.hints;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import java.io.IOException;

public class MyRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(CharSequence.class, MemberCategory.INVOKE_PUBLIC_METHODS);

        hints.reflection().registerType(IOException.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
    }
}
