/**
 *
 */
package cn.jiiiiiin.security.core.authorize;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认的授权配置管理器
 * <p>
 * 将下面的权限配置进行抽象
 * <p>
 * .and()
 * // 对请求进行授权，这个方法下面的都是授权的配置
 * .authorizeRequests()
 * // 添加匹配器，匹配器必须要放在`.anyRequest().authenticated()`之前配置
 * // 配置授权，允许匹配的请求不需要进行认证（permitAll()）
 * // https://docs.spring.io/spring-security/site/docs/4.2.7.RELEASE/reference/htmlsingle/#authorize-requests
 * .antMatchers(
 * SecurityConstants.STATIC_RESOURCES_JS,
 * SecurityConstants.DEFAULT_UNAUTHENTICATED_URL,
 * ...
 * registerUrl
 * )
 * // 允许上面的接口无需登录就能访问
 * .permitAll()
 * // 只有具有“ADMIN”角色的认证用户才能访问“/admin”接口
 * .antMatchers("/admin").hasRole("ADMIN")
 * // 匹配/admin/[id]这样的接口
 * .antMatchers("/admin/*").hasRole("ADMIN")
 * // 匹配接口（且匹配接口action）
 * .antMatchers(HttpMethod.GET,"/admin/me").hasRole("USER")
 * // 对其他的所有请求
 * .anyRequest()
 * // 都需要身份认证
 * .authenticated()
 *
 * **.anyRequest()**的配置需要放在其他antMatchers之后，因为会存在覆盖关系，故AuthorizeConfigProvider的定义需要指定先后顺序，使用@Order
 *
 * @author zhailiang
 * @see HttpSecurity#authorizeRequests()
 */
@Component
@AllArgsConstructor
@Slf4j
public class AuthorizeConfigManagerImpl implements AuthorizeConfigManager {

    /**
     * 收集自定义授权配置组件
     */
    private final List<AuthorizeConfigProvider> authorizeConfigProviders;

    @Override
    public void config(ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry config) {
        boolean existAnyRequestConfig = false;
        String existAnyRequestConfigName = null;

        for (AuthorizeConfigProvider authorizeConfigProvider : authorizeConfigProviders) {
            log.debug("AuthorizeConfigManagerImpl#config 安全模块配置 {}", authorizeConfigProvider.getClass().getName());
            boolean currentIsAnyRequestConfig = authorizeConfigProvider.config(config);
            if (existAnyRequestConfig && currentIsAnyRequestConfig) {
                throw new RuntimeException("重复的anyRequest配置:" + existAnyRequestConfigName + ","
                        + authorizeConfigProvider.getClass().getSimpleName());
            } else if (currentIsAnyRequestConfig) {
                existAnyRequestConfig = true;
                existAnyRequestConfigName = authorizeConfigProvider.getClass().getSimpleName();
            }
        }

        if (!existAnyRequestConfig) {
            config.anyRequest().authenticated();
        }
    }

}
