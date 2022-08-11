package com.jmal.clouddisk.office.model.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Action {
    @JsonProperty("edit")
    edit,
    @JsonProperty("review")
    review,
    @JsonProperty("view")
    view,
    @JsonProperty("embedded")
    embedded,
    @JsonProperty("filter")
    filter,
    @JsonProperty("comment")
    comment,
    @JsonProperty("fillForms")
    fillForms,
    @JsonProperty("blockcontent")
    blockcontent;
}
