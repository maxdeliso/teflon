package name.maxdeliso.teflon.config;

import com.google.gson.Gson;

import java.io.FileReader;
import java.io.IOException;
import java.util.Optional;

public class ConfigLoader {
    private final Gson gson;

    public ConfigLoader(final Gson gson) {
        this.gson = gson;
    }

    public Optional<Config> loadFromFile(String path) {
        try (final FileReader fileReader = new FileReader(path)) {
            return Optional.ofNullable(gson.fromJson(fileReader, Config.class));
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return Optional.empty();
        }
    }
}
