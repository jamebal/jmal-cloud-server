package com.jmal.clouddisk.controller.record;

public record MfaSetupResponse(Boolean mfaEnable, String secret, String qrCodeImageUri) {}
