package com.jmal.clouddisk.controller.record;

public record MfaVerifyRequest(String secret, String code) {}
