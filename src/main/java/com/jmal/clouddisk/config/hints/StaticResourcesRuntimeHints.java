package com.jmal.clouddisk.config.hints;

import org.jetbrains.annotations.NotNull;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class StaticResourcesRuntimeHints implements RuntimeHintsRegistrar {

    private static final String MENU_DB_PATH = "db/menu.json";
    private static final String ROLE_DB_PATH = "db/role.json";
    private static final String I18N_PROP = "i18n/messages.properties";
    private static final String I18N_ZH_CN_PROP = "i18n/messages_zh_CN.properties";
    private static final String I18N_EN_US_PROP = "i18n/messages_en_US.properties";

    @Override
    public void registerHints(@NotNull RuntimeHints hints, ClassLoader classLoader) {
        try {
            hints.resources().registerPattern(MENU_DB_PATH);
            hints.resources().registerPattern(ROLE_DB_PATH);
            hints.resources().registerPattern(I18N_PROP);
            hints.resources().registerPattern(I18N_ZH_CN_PROP);
            hints.resources().registerPattern(I18N_EN_US_PROP);
        } catch (Exception e) {
            // 在构建时抛出异常，以便立即发现问题
            throw new RuntimeException("Failed to write document classes resource file.", e);
        }
    }
}
