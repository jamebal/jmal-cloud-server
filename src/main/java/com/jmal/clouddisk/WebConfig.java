package com.jmal.clouddisk;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebConfig
 *
 * @blame jmal
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	/**
	 * 注册拦截器
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
//		registry.addInterceptor(new AuthInterceptor()).addPathPatterns("/**").
//				excludePathPatterns("/login","/public");
		registry.addInterceptor(new AuthInterceptor()).addPathPatterns("/rwlock").
				excludePathPatterns("/**");
	}
}
