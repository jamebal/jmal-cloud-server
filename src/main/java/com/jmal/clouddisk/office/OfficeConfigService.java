package com.jmal.clouddisk.office;

import cn.hutool.core.codec.Base62;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.KeyUtil;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.jmal.clouddisk.office.model.OfficeConfigDO;
import com.jmal.clouddisk.office.model.OfficeConfigDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OfficeConfigService {

    public final static String VO_KEY = "****************";

    private final MongoTemplate mongoTemplate;

    private volatile OfficeConfigDTO officeConfigDTO;

    private OfficeConfigDTO getOfficeConfigCache() {
        if (officeConfigDTO == null) {
            synchronized (this) {
                if (officeConfigDTO == null) {
                    OfficeConfigDO officeConfigDO = mongoTemplate.findOne(new Query(), OfficeConfigDO.class);
                    if (officeConfigDO == null) {
                        officeConfigDTO = new OfficeConfigDTO();
                    } else {
                        officeConfigDTO = officeConfigDO.toOfficeConfigCache();
                    }
                }
            }
        }
        return officeConfigDTO;
    }

    public String createOfficeToken(final Map<String, Object> payloadClaims) {
        String secret = officeConfigDTO.getSecret();
        if (StrUtil.isBlank(secret)) {
            return "";
        }
        final JWTSigner signer = JWTSignerUtil.hs256(secret.getBytes());
        JWT jwt = JWT.create().addPayloads(payloadClaims);
        return jwt.sign(signer);
    }

    public OfficeConfigDTO getOfficeConfig() {
        OfficeConfigDTO officeConfigDTO = getOfficeConfigCache();
        OfficeConfigDTO officeConfigVO = new OfficeConfigDTO();
        officeConfigVO.setDocumentServer(officeConfigDTO.getDocumentServer());
        officeConfigVO.setCallbackServer(officeConfigDTO.getCallbackServer());
        officeConfigVO.setTokenEnabled(officeConfigDTO.isTokenEnabled());
        if (StrUtil.isNotBlank(officeConfigDTO.getSecret())) {
            officeConfigVO.setSecret(VO_KEY);
        }
        return officeConfigVO;
    }

    public void setOfficeConfig(OfficeConfigDTO officeConfigDTO) {
        OfficeConfigDO officeConfigDO = officeConfigDTO.toOfficeConfigDO();
        Query query = new Query();
        Update update = new Update()
                .set("documentServer", officeConfigDO.getDocumentServer())
                .set("callbackServer", officeConfigDO.getCallbackServer())
                .set("tokenEnabled", officeConfigDO.getTokenEnabled())
                .set("format", officeConfigDO.getFormat());
        if (!VO_KEY.equals(officeConfigDTO.getSecret())) {
            update.set("encrypted", officeConfigDO.getEncrypted());
            update.set("key", officeConfigDO.getKey());
        }
        mongoTemplate.upsert(query, update, OfficeConfigDO.class);

        // 重置缓存的密钥，使其在下次访问时重新加载
        synchronized (this) {
            if (VO_KEY.equals(officeConfigDTO.getSecret())) {
                return;
            }
            this.officeConfigDTO = officeConfigDTO;
        }
    }

    public static String generateKey() {
        byte[] keyBytes = KeyUtil.generateKey(SymmetricAlgorithm.AES.getValue(), 256).getEncoded();
        String base62Key = Base62.encode(keyBytes);
        return base62Key.length() > 32 ? base62Key.substring(0, 32) : base62Key;
    }
}
