package com.jmal.clouddisk.webdav;

public enum WebdavMethod {
	HEAD("HEAD", false),
	PROPFIND("PROPFIND", false),
	PROPPATCH("PROPPATCH", true),
	MKCOL("MKCOL", true),
	MKCALENDAR("MKCALENDAR", true),
	COPY("COPY", true),
	MOVE("MOVE", true),
	LOCK("LOCK", true),
	UNLOCK("UNLOCK", true),
	DELETE("DELETE", true),
	GET("GET", false),
	OPTIONS("OPTIONS", false),
	POST("POST", true),
	PUT("PUT", true),
	TRACE("TRACE", false),
	ACL("ACL", true),
	CONNECT("CONNECT", true),
	CANCELUPLOAD("CANCELUPLOAD", true),
	REPORT("REPORT", false);

	public String getCode() {
		return code;
	}

	public final String code;
	public final boolean isWrite;

	WebdavMethod(String code, boolean isWrite) {
		this.code = code;
		this.isWrite = isWrite;
	}

	public static WebdavMethod getMethod(String method) {
		for (WebdavMethod webdavMethod : WebdavMethod.values()) {
			if (webdavMethod.code.equals(method)) {
				return webdavMethod;
			}
		}
		return WebdavMethod.HEAD;
	}
}
