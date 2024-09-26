package com.jmal.clouddisk.util;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MessageUtil {

    private final MessageSource messageSource;

    /**
     * 获取国际化消息
     * @param messageKey 消息的键
     * @return 国际化消息
     */
    public String getMessage(String messageKey) {
        return getMessage(messageKey, LocaleContextHolder.getLocale());
    }

    /**
     * 获取国际化消息
     * @param messageKey 消息的键
     * @param locale 语言
     * @return 国际化消息
     */
    public String getMessage(String messageKey, Locale locale) {
        String result = messageKey;
        try {
            result = messageSource.getMessage(messageKey, null, locale);
        } catch (NoSuchMessageException ignored) {
        }
        return result;
    }

}
