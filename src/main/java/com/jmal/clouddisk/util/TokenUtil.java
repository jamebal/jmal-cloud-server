package com.jmal.clouddisk.util;


import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

/**
 * @author jmal
 */
public class TokenUtil {

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
        JWTCreator.Builder builder = JWT.create();
        builder.withClaim("username", key);
        if (localDataTime != null) {
            builder.withExpiresAt(Date.from(localDataTime.atZone(ZoneId.systemDefault()).toInstant()));
        }
        byte[] keyBytes = password.getBytes(StandardCharsets.UTF_8);
        return builder.sign(Algorithm.HMAC256(keyBytes));

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
