package com.jmal.clouddisk.oss.s3;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AwsS3ServiceTest {

    @Test
    void shouldKeepEndpointWithExplicitScheme() {
        URI endpoint = AwsS3Service.normalizeEndpoint(" https://s3.example.com ");

        assertEquals("https://s3.example.com", endpoint.toString());
    }

    @Test
    void shouldAcceptEndpointWithoutScheme() {
        URI endpoint = AwsS3Service.normalizeEndpoint("localhost:9000");

        assertEquals("http://localhost:9000", endpoint.toString());
    }

    @Test
    void shouldRejectBlankOrInvalidEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> AwsS3Service.normalizeEndpoint(" "));
        assertThrows(IllegalArgumentException.class, () -> AwsS3Service.normalizeEndpoint("http:///missing-host"));
    }
}
