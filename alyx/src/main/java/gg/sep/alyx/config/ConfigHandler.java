package gg.sep.alyx.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.harawata.appdirs.AppDirs;
import net.harawata.appdirs.AppDirsFactory;

import gg.sep.alyx.plugin.model.AlyxConfig;
import gg.sep.alyx.plugin.model.BotConfig;
import gg.sep.alyx.plugin.model.BotEntry;
import gg.sep.alyx.plugin.util.ModelParser;

/**
 * {@link ConfigHandler} is responsible for creating, updating, and reading an Alyx config files.
 *
 * The config file used is specified by the user as a CLI argument when launching Alyx,
 * or a default "app dirs" file is created and used based on the user's operating system.
 */
@Log4j2
@RequiredArgsConstructor
public class ConfigHandler {

    private static final String APP_NAME = "alyx-discord-bot";
    private static final String APP_AUTHOR = "sep";
    private static final String CONFIG_VERSION = "0.1"; // Config Version will rarely change.
    private static final boolean ROAMING = true;

    private static final AppDirs APP_DIRS = AppDirsFactory.getInstance();
    private static final String DATA_DIR = APP_DIRS.getUserDataDir(APP_NAME, CONFIG_VERSION, APP_AUTHOR, ROAMING);
    private static final String CONFIG_DIR = APP_DIRS.getUserConfigDir(APP_NAME, CONFIG_VERSION, APP_AUTHOR, ROAMING);
    private static final String ALYX_CONFIG_PATH = CONFIG_DIR + "/alyx-discord-bot.json";

    public static final Path ALYX_DEFAULT_CONFIG_FILE = Path.of(ALYX_CONFIG_PATH);
    public static final Path ALYX_DEFAULT_DATA_DIR = Path.of(DATA_DIR);

    @Getter private final Path configPath;

    /**
     * Attempts to load and parse the config file into an {@link AlyxConfig}.
     * @return Returns an {@link Optional} of the {@link AlyxConfig} if successful, otherwise an empty Optional.
     */
    public Optional<AlyxConfig> loadAlyxConfig() {
        try {
            return ModelParser.parseJson(readConfig(configPath), AlyxConfig.class);
        } catch (final IOException e) {
            log.error(e);
        }
        return Optional.empty();
    }

    /**
     * Attempts to load and parse the config file into an {@link BotConfig}.
     *
     * @param botEntry The BotEntry for which to load the bot's config.
     * @return Returns an {@link Optional} of the {@link BotConfig} if successful, otherwise an empty Optional.
     */
    public Optional<BotConfig> loadBotConfig(final BotEntry botEntry) {
        try {
            final Path botConfigPath = botEntry.getDataDir()
                .resolve(String.format("%s_config.json", botEntry.getBotName()));
            return ModelParser.parseJson(readConfig(botConfigPath), BotConfig.class);
        } catch (final IOException e) {
            log.error(e);
        }
        return Optional.empty();
    }

    /**
     * Updates or creates a bot entry in the config file, overwriting any existing bot entry with the same name.
     * @param botEntry Bot Entry to update in the config. If it doesn't exist, it will be created.
     * @throws IOException Exception thrown if writing to the config file failed.
     */
    public void updateBotEntry(final BotEntry botEntry) throws IOException {
        final Optional<AlyxConfig> currentConfig = loadAlyxConfig();

        AlyxConfig outputConfig = currentConfig.orElse(null);
        if (outputConfig == null) {
            outputConfig = generateBlankAlyxConfig();
        }
        outputConfig.getBots().put(botEntry.getBotName(), botEntry);
        writeConfig(outputConfig);
    }

    /**
     * Updates a bot's config file, overwriting any existing bot config.
     * @param botEntry Bot Entry for which to update the config.
     * @param botConfig The Bot Config to write.
     * @throws IOException Exception thrown if writing to the config file failed.
     */
    public void updateBotConfig(final BotEntry botEntry, final BotConfig botConfig) throws IOException {
        writeBotConfig(botEntry, botConfig);
    }

    /**
     * Write the AlyxConfig to the config file, overwriting any existing configuration.
     * @param alyxConfig Alyx Config to write to disk.
     * @throws IOException Exception thrown if writing to the config file failed.
     */
    public void writeConfig(final AlyxConfig alyxConfig) throws IOException {
        ensureConfigFile(configPath);
        Files.writeString(configPath, alyxConfig.toPrettyJson());
    }

    /**
     * Write the {@link BotConfig} to the config file, overwriting any existing configuration.
     * @param botEntry The BotEntry for the bot's config.
     * @param botConfig BotConfig to write to disk.
     * @throws IOException Exception thrown if writing to the config file failed.
     */
    public void writeBotConfig(final BotEntry botEntry, final BotConfig botConfig) throws IOException {
        final String fileName = String.format("%s_config.json", botEntry.getBotName());
        final Path botConfigPath = botEntry.getDataDir().resolve(fileName);
        ensureConfigFile(botConfigPath);
        Files.writeString(botConfigPath, botConfig.toPrettyJson());
    }

    private String readConfig(final Path filePath) throws IOException {
        return Files.readString(filePath);
    }

    private AlyxConfig generateBlankAlyxConfig() throws IOException {
        final AlyxConfig blankConfig = AlyxConfig.empty();
        writeConfig(blankConfig);
        return blankConfig;
    }

    private void ensureConfigFile(final Path filePath) throws IOException {
        final Path parentPath = filePath.getParent();
        if (parentPath == null) {
            throw new IOException("Config file path cannot be a root directory.");
        }
        if (!Files.exists(configPath)) {
            Files.createDirectories(parentPath);
            Files.createFile(configPath);
        }
    }
}
