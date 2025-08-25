package com.jmal.clouddisk.controller.record;

import com.jmal.clouddisk.config.Reflective;

public record MfaVerifyRequest(String mfaToken, String username, String secret, String code) implements Reflective {}
