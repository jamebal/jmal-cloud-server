package com.jmal.clouddisk.dao.mapping;

import lombok.Getter;

@Getter
public enum UserField implements FieldMapping {
    MFA_ENABLED("mfaEnabled", "mfaEnabled", "mfa_enabled"),
    WEBP_DISABLED("webpDisabled", "webpDisabled", "webp_disabled"),
    TAKE_UP_SPACE("takeUpSpace", "takeUpSpace", "take_up_space"),
    UPDATED_TIME("updatedTime", "updatedTime", "updated_time"),
    MFA_SECRET("mfaSecret", "mfaSecret", "mfa_secret"),
    SHOW_NAME("showName", "showName", "show_name"),
    CREATED_TIME("createdTime", "createdTime", "created_time"),
    ;

    private final String logical, mongo, jpa;

    UserField(String logical, String mongo, String jpa) {
        this.logical = logical;
        this.mongo = mongo;
        this.jpa = jpa;
    }

    public static FieldMapping[] allFields() {
        FieldMapping[] commonFields = CommonField.values();
        FieldMapping[] userFields = UserField.values();
        FieldMapping[] result = new FieldMapping[commonFields.length + userFields.length];

        System.arraycopy(commonFields, 0, result, 0, commonFields.length);
        System.arraycopy(userFields, 0, result, commonFields.length, userFields.length);

        return result;
    }
}
