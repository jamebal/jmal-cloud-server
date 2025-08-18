package com.jmal.clouddisk.controller.record;

import com.jmal.clouddisk.config.Reflective;

public record MfaSetupResponse(Boolean mfaEnable, String secret, String qrCodeImageUri) implements Reflective {}
