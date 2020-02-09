package com.jmal.clouddisk.util;


import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class TokenUtil {


    private static final String SECRET = "JKKLJOoasdlfj";
    private static final int calendarField = Calendar.DATE;
    private static final int calendarInterval = 7;


    /**
     * 生成token
     * @param name
     * @return
     */
    public static String createTokens(String name) {
        Date iatDate = new Date();
        Calendar nowTime = Calendar.getInstance();
        nowTime.add(calendarField, calendarInterval);
        Date expiresDate = nowTime.getTime();
        Map<String, Object> map = new HashMap<>();
        map.put("alg", "HS256");
        map.put("typ", "JWT");
        String token = JWT.create().withHeader(map) // header
                .withClaim("iss", "Service") // payload
                .withClaim("aud", "WEB").withClaim("username", name).withIssuedAt(iatDate) // sign time
                .withExpiresAt(expiresDate) // expire time
                .sign(Algorithm.HMAC256(SECRET)); // signature
        return token;
    }


    public static String getUserName(String token) {
        // 获取username
        Map<String, Claim> claims = verifyToken(token);
        Claim userName_claim = claims.get("username");
        if (null == userName_claim || userName_claim.asString() == null || "".equals(userName_claim.asString())) {
            return null;
        }
        return userName_claim.asString();
    }

    public static Map<String, Claim> verifyToken(String token) {
        DecodedJWT jwt = null;
        try {
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(SECRET)).build();
            jwt = verifier.verify(token);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jwt.getClaims();
    }

}
