package com.jmal.clouddisk.controller.record;

import com.jmal.clouddisk.config.Reflective;

public record MfaVerifyRequest(String secret, String code) implements Reflective {}
