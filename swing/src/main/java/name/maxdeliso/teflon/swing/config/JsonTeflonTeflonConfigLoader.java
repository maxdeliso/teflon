package name.maxdeliso.teflon.swing.config;


import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.util.Optional;

public class JsonTeflonTeflonConfigLoader implements TeflonConfigLoader {
    private static final Logger LOG = LoggerFactory.getLogger(JsonTeflonTeflonConfigLoader.class);

    private final Gson gson;

    public JsonTeflonTeflonConfigLoader(final Gson gson) {
        this.gson = gson;
    }

    public Optional<TeflonConfig> loadFromFile(String path) {
        try (final var fileReader = new FileReader(path)) {
            return Optional.ofNullable(gson.fromJson(fileReader, TeflonConfig.class));
        } catch (final IOException ioe) {
            LOG.error("failed to load config", ioe);
            return Optional.empty();
        }
    }
}
