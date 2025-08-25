package com.jmal.clouddisk.controller.record;

import com.jmal.clouddisk.config.Reflective;

public record LoginResponse(Boolean mfaForceEnable, Boolean mfaRequired, String mfaToken) implements Reflective {}
