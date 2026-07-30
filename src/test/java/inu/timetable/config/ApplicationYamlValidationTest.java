package inu.timetable.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationYamlValidationTest {

    @Test
    void productionApplicationYamlHasNoDuplicateKeys() throws Exception {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));
        Path applicationYaml = Path.of("src", "main", "resources", "application.yml");

        Map<?, ?> properties;
        try (InputStream inputStream = Files.newInputStream(applicationYaml)) {
            properties = yaml.load(inputStream);
        }

        assertThat(properties.containsKey("subject")).isTrue();
        Map<?, ?> subjectProperties = (Map<?, ?>) properties.get("subject");
        assertThat(subjectProperties.containsKey("import")).isTrue();
        assertThat(subjectProperties.containsKey("cache")).isTrue();
    }
}
