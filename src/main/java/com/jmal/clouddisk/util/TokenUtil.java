package com.jmal.clouddisk.util;


import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jmal
 */
@Component
public class TokenUtil {

    private static String secret = null;

    private TokenUtil() {
        secret = HexUtil.encodeHexStr(SecureUtil.generateKey(SymmetricAlgorithm.AES.getValue()).getEncoded());
    }

    /**
     * 生成token
     *
     * @param key token key
     * @param password password
     * @param localDataTime 过期时间
     * @return token
     */
    public static String createToken(String key, String password, LocalDateTime localDataTime) {
        return generateToken(key, password, localDataTime);
    }

    private static String generateToken(String key, String password, LocalDateTime localDataTime) {
        Map<String, Object> map = new HashMap<>(3);
        map.put("alg", "HS256");
        map.put("typ", "JWT");
        JWTCreator.Builder builder = JWT.create();
        builder.withHeader(map)
                // payload
                .withClaim("iss", "Service")
                // sign time
                .withClaim("aud", "WEB").withClaim("username", key);
        if (localDataTime != null) {
            builder.withExpiresAt(Date.from(localDataTime.atZone(ZoneId.systemDefault()).toInstant()));
        }
        return builder.sign(Algorithm.HMAC256(password));
    }

    /**
     * 生成token
     *
     * @param key token key
     * @return token
     */
    public static String createToken(String key, LocalDateTime localDateTime) {
        return generateToken(key, secret, localDateTime);
    }

    public static String getTokenKey(String token) {
        return getTokenKey(token, secret);
    }

    public static String getTokenKey(String token, String password) {
        // 获取username
        Map<String, Claim> claims = verifyToken(token, password);
        if (claims.isEmpty()) {
            return null;
        }
        Claim userNameClaim = claims.get("username");
        if (null == userNameClaim || userNameClaim.asString() == null || "".equals(userNameClaim.asString())) {
            return null;
        }
        return userNameClaim.asString();
    }

    private static Map<String, Claim> verifyToken(String token, String password) {
        DecodedJWT jwt;
        try {
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(password)).build();
            jwt = verifier.verify(token);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
        if (jwt != null) {
            return jwt.getClaims();
        }
        return Collections.emptyMap();
    }

}
