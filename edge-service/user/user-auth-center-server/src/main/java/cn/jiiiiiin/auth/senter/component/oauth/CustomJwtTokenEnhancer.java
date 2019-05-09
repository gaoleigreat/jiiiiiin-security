/**
 *
 */
package cn.jiiiiiin.security.app.server;

import cn.jiiiiiin.module.mngauth.component.MngUserDetails;
import cn.jiiiiiin.security.core.properties.OAuth2ClientProperties;
import cn.jiiiiiin.security.core.properties.SecurityProperties;
import lombok.AllArgsConstructor;
import lombok.val;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static cn.jiiiiiin.security.core.properties.OAuth2ClientProperties.DEFAULT_ACCESSTOKEN_VALIDATESECONDS;

/**
 * 扩展和解析JWT的信息
 *
 * 将自定义的信息加入到token中返回给客户端
 *
 * @author zhailiang
 * @author jiiiiiin
 */
@AllArgsConstructor
public class CustomJwtTokenEnhancer implements TokenEnhancer {

    /**
     * token增强存储认证服务器认证之后的{@link org.springframework.security.core.userdetails.UserDetails}的key
     */
    public static final String CACHE_PRINCIPAL = "cache_principal";
    private static final String TOKENENHANCER = "TokenEnhancer-userDetails-";
    private static final OAuth2ClientProperties DEF_CLIENT_INFO = new OAuth2ClientProperties().setAccessTokenValidateSeconds(DEFAULT_ACCESSTOKEN_VALIDATESECONDS);
    /**
     * 默认延迟清理附加时间（秒）
     */
    private static final int DEF_DELAY = 30;
    private final RedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;

    @Override
    public OAuth2AccessToken enhance(OAuth2AccessToken accessToken, OAuth2Authentication authentication) {
        // 自定义token元信息
        final Map<String, Object> info = new HashMap<>();
        val principal = authentication.getUserAuthentication().getPrincipal();
        if (principal instanceof MngUserDetails) {
            val userDetails = (MngUserDetails) principal;
            val key = generateTokenEnhancerUserDetailsKey(userDetails);
            val clientId = authentication.getOAuth2Request().getClientId();
            val accessTokenValiditySeconds = _parseAccessTokenValiditySeconds(clientId, securityProperties.getOauth2().getClients()) + DEF_DELAY;
            // 找到对应的client信息，以配置的token失效时间+延迟 来设置这里存储的过期时间
            redisTemplate.opsForValue().set(key, userDetails.getAdmin(), accessTokenValiditySeconds, TimeUnit.SECONDS);
            info.put(CACHE_PRINCIPAL, key);
        }

        // 设置附加信息
        ((DefaultOAuth2AccessToken) accessToken).setAdditionalInformation(info);
        return accessToken;
    }

    private int _parseAccessTokenValiditySeconds(String clientId, List<OAuth2ClientProperties> clients) {
        // Optional::
        // https://www.jianshu.com/p/82ed16613072
        return clients.stream()
                .filter(clientInfo -> clientId.equalsIgnoreCase(clientInfo.getClientId()))
                .findFirst()
                .orElse(DEF_CLIENT_INFO).getAccessTokenValidateSeconds();
    }

    private static String generateTokenEnhancerUserDetailsKey(MngUserDetails userDetails) {
        return TOKENENHANCER.concat(String.valueOf(userDetails.getAdmin().getId()));
    }

}