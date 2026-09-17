package ai.typesafe.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SdkVersionTest {

    @Test
    void versionMatchesPom() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        Matcher m = Pattern.compile("<artifactId>typesafe-sdk</artifactId>\\s*<version>([^<]+)</version>").matcher(pom);
        assertTrue(m.find(), "project version not found in pom.xml");
        assertEquals(m.group(1), SdkVersion.VERSION);
        assertEquals("typesafe-sdk-java/" + SdkVersion.VERSION, SdkVersion.USER_AGENT);
    }
}
