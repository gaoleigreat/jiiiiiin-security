/**
 *
 */
package cn.jiiiiiin.security.core.social.controller;

import cn.jiiiiiin.security.core.dict.SecurityConstants;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.ConnectionData;
import org.springframework.social.connect.ConnectionFactoryLocator;
import org.springframework.social.connect.UsersConnectionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

import java.util.concurrent.TimeUnit;

/**
 * app环境下替换`providerSignInUtils`，以便两个环节公用，避免切换时候修改代码
 *
 * 避免由于没有session导致读不到社交用户信息的问题
 *
 * @author zhailiang
 * @see org.springframework.social.connect.web.ProviderSignInUtils#doPostSignUp(String, RequestAttributes) 和这个类的作用一直，spring默认实现提供的是将 {@link ConnectionData}存储在session
 */
@Component
@AllArgsConstructor
@ConditionalOnBean(name = "dataSource")
public class SocialCommSingUpUtils {

    private final RedisTemplate<String, Object> redisTemplate;

    private final UsersConnectionRepository usersConnectionRepository;

    private final ConnectionFactoryLocator connectionFactoryLocator;

    /**
     * 缓存社交网站用户信息到redis
     * <p>
     * 默认10分钟过期，即用户在10分钟之内没有获取这条数据，如去绑定或者注册，那么久自动销毁
     *
     * @param request
     * @param connectionData 存储了第三方社交登录获取的授权用户信息
     */
    public void saveConnectionData(WebRequest request, ConnectionData connectionData) {
        redisTemplate.opsForValue().set(getKey(request), connectionData, 10, TimeUnit.MINUTES);
    }

    /**
     * 将缓存的社交网站用户信息与系统注册用户信息绑定
     *
     * 及完成了（新注册）系统用户和social社交用户的绑定
     *
     * @param request
     * @param userId 业务系统的用户id
     * @see org.springframework.social.connect.web.ProviderSignInUtils#doPostSignUp(String, RequestAttributes) 和这个类的作用一直，spring默认实现提供的是将 {@link ConnectionData}存储在session
     */
    public void doPostSignUp(WebRequest request, String userId) {
        String key = getKey(request);
        if (!redisTemplate.hasKey(key)) {
            throw new RuntimeException("无法找到缓存的用户社交账号信息");
        }
        ConnectionData connectionData = (ConnectionData) redisTemplate.opsForValue().get(key);
        assert connectionData != null;
        Connection<?> connection = connectionFactoryLocator.getConnectionFactory(connectionData.getProviderId())
                .createConnection(connectionData);
        // 将数据存储到`UserConnection`表中
        usersConnectionRepository.createConnectionRepository(userId).addConnection(connection);

        redisTemplate.delete(key);
    }

    /**
     * 获取redis key
     *
     * @param request
     * @return
     */
    private String getKey(WebRequest request) {
        String deviceId = request.getHeader(SecurityConstants.DEFAULT_PARAMETER_NAME_DEVICEID);
        if (StringUtils.isBlank(deviceId)) {
            throw new RuntimeException("设备id参数不能为空");
        }
        return "security:social.connect." + deviceId;
    }

}
