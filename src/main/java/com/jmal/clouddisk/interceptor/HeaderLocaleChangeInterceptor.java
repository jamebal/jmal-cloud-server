package com.jmal.clouddisk.interceptor;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.Locale;

public class HeaderLocaleChangeInterceptor extends LocaleChangeInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        String lang = request.getHeader("lang");
        if (StrUtil.isNotBlank(lang) && lang.indexOf("_") > 0) {
            Locale locale = Locale.forLanguageTag(lang.replace("_", "-"));
            LocaleContextHolder.setLocale(locale);
        } else {
            LocaleContextHolder.setLocale(Locale.US);
        }
        return true;
    }
}
