package net.trilleo.rhythmcraft.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Utility class for saving and loading {@link Chart} objects as JSON files. */
public final class ChartSerializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ChartSerializer() {}

    /** Serialises a chart to a pretty-printed JSON string. */
    public static String toJson(Chart chart) {
        return GSON.toJson(chart);
    }

    /** Deserialises a chart from a JSON string. */
    public static Chart fromJson(String json) {
        Chart c = GSON.fromJson(json, Chart.class);
        if (c == null) c = new Chart();
        if (c.notes == null) c.notes = new ArrayList<>();
        return c;
    }

    /** Saves a chart to {@code path} (parent directories are created if needed). */
    public static void save(Chart chart, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, toJson(chart), StandardCharsets.UTF_8);
    }

    /** Loads a chart from {@code path}. */
    public static Chart load(Path path) throws IOException {
        return fromJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    /**
     * Returns the default charts directory: {@code <game_dir>/rhythmcraft/charts/}.
     * The directory is created lazily by {@link #save}.
     */
    public static Path getChartsDir() {
        return FabricLoader.getInstance().getGameDir()
                .resolve("rhythmcraft")
                .resolve("charts");
    }
}
