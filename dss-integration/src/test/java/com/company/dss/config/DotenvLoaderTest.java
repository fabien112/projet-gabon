package com.company.dss.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DotenvLoaderTest {

    private static final String KEY = "DOTENV_TEST_UNIQUE_KEY";

    @AfterEach
    void clear() {
        System.clearProperty(KEY);
        System.clearProperty("DOTENV_TEST_QUOTED");
    }

    @Test
    void loadFrom_setsPropertiesAndIgnoresComments(@TempDir Path dir) throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(
                env,
                "# commentaire\n" + KEY + "=10.1.2.3\nDOTENV_TEST_QUOTED=\"hello world\"\n",
                StandardCharsets.UTF_8);

        DotenvLoader.loadFrom(env);

        assertThat(System.getProperty(KEY)).isEqualTo("10.1.2.3");
        assertThat(System.getProperty("DOTENV_TEST_QUOTED")).isEqualTo("hello world");
    }

    @Test
    void loadFrom_overridesExistingSystemProperty(@TempDir Path dir) throws Exception {
        System.setProperty(KEY, "old-value");
        Path env = dir.resolve(".env");
        Files.writeString(env, KEY + "=from-file\n", StandardCharsets.UTF_8);

        DotenvLoader.loadFrom(env);

        assertThat(System.getProperty(KEY)).isEqualTo("from-file");
    }
}
