package com.jmal.clouddisk.config.hints;

import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import java.util.List;

public class MyRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection()
             .registerType(CharSequence.class, hint ->
                 hint.withMethod("length", List.of(), ExecutableMode.INVOKE)
             );
    }
}
