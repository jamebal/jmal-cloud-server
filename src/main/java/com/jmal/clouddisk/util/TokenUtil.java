package com.jmal.clouddisk.util;


import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jmal
 */
@Slf4j
public class TokenUtil {

    private static final String SECRET = "JKKLJOoasdlfj";

    /**
     * 生成token
     *
     * @param name
     * @return
     */
    public static String createTokens(String name) {
        Map<String, Object> map = new HashMap<>(3);
        map.put("alg", "HS256");
        map.put("typ", "JWT");
        // header
        return JWT.create().withHeader(map)
                // payload
                .withClaim("iss", "Service")
                // sign time
                .withClaim("aud", "WEB").withClaim("username", name)
                // signature
                .sign(Algorithm.HMAC256(SECRET));
    }

    public static String getUserName(String token) {
        // 获取username
        Map<String, Claim> claims = verifyToken(token);
        if (claims == null) {
            return null;
        }
        Claim userNameClaim = claims.get("username");
        if (null == userNameClaim || userNameClaim.asString() == null || "".equals(userNameClaim.asString())) {
            return null;
        }
        return userNameClaim.asString();
    }

    private static Map<String, Claim> verifyToken(String token) {
        DecodedJWT jwt = null;
        try {
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(SECRET)).build();
            jwt = verifier.verify(token);
        } catch (TokenExpiredException e) {
            return null;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        if (jwt != null) {
            return jwt.getClaims();
        }
        return null;
    }

//    public static void main(String[] args) {
//        String token = createTokens("jmal");
//        String jmal = getUserName(token);
//        Console.log(jmal);
//    }

}
