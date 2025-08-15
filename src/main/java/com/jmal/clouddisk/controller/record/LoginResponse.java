package com.jmal.clouddisk.controller.record;

import com.jmal.clouddisk.config.Reflective;

public record LoginResponse(String token, boolean mfaRequired, String mfaToken) implements Reflective {}
