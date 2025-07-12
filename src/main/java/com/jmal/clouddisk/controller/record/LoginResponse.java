package com.jmal.clouddisk.controller.record;

public record LoginResponse(String token, boolean mfaRequired, String mfaToken) {}
