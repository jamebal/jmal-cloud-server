package com.jmal.clouddisk.office;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.jmal.clouddisk.dao.IOfficeConfigDAO;
import com.jmal.clouddisk.office.model.OfficeConfigDO;
import com.jmal.clouddisk.office.model.OfficeConfigDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OfficeConfigService {

    public final static String VO_KEY = "****************";

    private final IOfficeConfigDAO officeConfigDAO;

    private final TextEncryptor textEncryptor;

    private volatile OfficeConfigDTO officeConfigDTO;

    private OfficeConfigDTO getOfficeConfigCache() {
        if (officeConfigDTO == null) {
            synchronized (this) {
                if (officeConfigDTO == null) {
                    OfficeConfigDO officeConfigDO = officeConfigDAO.findOne();
                    if (officeConfigDO == null) {
                        officeConfigDTO = new OfficeConfigDTO();
                    } else {
                        officeConfigDTO = officeConfigDO.toOfficeConfigCache(textEncryptor);
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
        officeConfigVO.setFormat(officeConfigDTO.getFormat());
        if (StrUtil.isNotBlank(officeConfigDTO.getSecret())) {
            officeConfigVO.setSecret(VO_KEY);
        }
        return officeConfigVO;
    }

    public void setOfficeConfig(OfficeConfigDTO officeConfigDTO) {
        OfficeConfigDO officeConfigDO = officeConfigDTO.toOfficeConfigDO(textEncryptor);
        officeConfigDAO.upsert(officeConfigDO);

        // 重置缓存的密钥，使其在下次访问时重新加载
        synchronized (this) {
            this.officeConfigDTO.setDocumentServer(officeConfigDTO.getDocumentServer());
            this.officeConfigDTO.setCallbackServer(officeConfigDTO.getCallbackServer());
            this.officeConfigDTO.setFormat(officeConfigDTO.getFormat());
            this.officeConfigDTO.setTokenEnabled(StrUtil.isNotBlank(officeConfigDTO.getSecret()));
            if (VO_KEY.equals(officeConfigDTO.getSecret())) {
                return;
            }
            if (BooleanUtil.isTrue(this.officeConfigDTO.isTokenEnabled())) {
                this.officeConfigDTO.setSecret(officeConfigDTO.getSecret());
            } else {
                this.officeConfigDTO.setSecret(null);
            }
        }
    }
}
