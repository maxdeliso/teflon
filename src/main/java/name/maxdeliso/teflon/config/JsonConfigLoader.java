package name.maxdeliso.teflon.config;


import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.util.Optional;

public class JsonConfigLoader implements ConfigLoader {
    private static final Logger LOG = LoggerFactory.getLogger(JsonConfigLoader.class);

    private final Gson gson;

    public JsonConfigLoader(final Gson gson) {
        this.gson = gson;
    }

    public Optional<Config> loadFromFile(String path) {
        try (final var fileReader = new FileReader(path)) {
            return Optional.ofNullable(gson.fromJson(fileReader, Config.class));
        } catch (final IOException ioe) {
            LOG.error("failed to load config", ioe);
            return Optional.empty();
        }
    }
}
