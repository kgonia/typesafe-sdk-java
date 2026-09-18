package io.github.kgonia.typesafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class SdkVersionTest {

    @Test
    void versionMatchesBuild() {
        String fromBuild = System.getProperty("sdk.version");
        assertNotNull(fromBuild, "surefire sets sdk.version from ${project.version}");
        assertEquals(fromBuild, SdkVersion.VERSION);
        assertEquals("typesafe-sdk-java/" + SdkVersion.VERSION, SdkVersion.USER_AGENT);
    }
}
