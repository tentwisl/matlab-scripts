package net.mca.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.mca.MCA;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON-backed layout for Nation UI. Stored at config/mca/nation/layout.json.
 */
public final class NationLayoutConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] KEYS = {"VillageList", "VillagerList", "ProfilePanel", "MapWidget", "ActionButtons"};

    private NationLayoutConfig() {
    }

    public static Path layoutPath() {
        Path runDir = MinecraftClient.getInstance().runDirectory.toPath();
        return runDir.resolve("config").resolve("mca").resolve("nation").resolve("layout.json");
    }

    public static Map<String, LayoutRect> loadOrCreateDefaults(int width, int height) {
        Map<String, LayoutRect> defaults = defaults(width, height);
        Path path = layoutPath();
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                save(path, defaults);
                return defaults;
            }
            String json = Files.readString(path);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                save(path, defaults);
                return defaults;
            }
            Map<String, LayoutRect> out = new LinkedHashMap<>();
            for (String key : KEYS) {
                if (root.has(key) && root.get(key).isJsonObject()) {
                    JsonObject o = root.getAsJsonObject(key);
                    out.put(key, new LayoutRect(
                            intOr(o, "x", defaults.get(key).x),
                            intOr(o, "y", defaults.get(key).y),
                            intOr(o, "w", defaults.get(key).w),
                            intOr(o, "h", defaults.get(key).h)
                    ));
                } else {
                    out.put(key, defaults.get(key));
                }
            }
            return out;
        } catch (Exception e) {
            MCA.LOGGER.warn("Failed to load nation layout config, using defaults", e);
            return defaults;
        }
    }

    public static void save(Path path, Map<String, LayoutRect> layout) throws IOException {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, LayoutRect> e : layout.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("x", e.getValue().x);
            o.addProperty("y", e.getValue().y);
            o.addProperty("w", e.getValue().w);
            o.addProperty("h", e.getValue().h);
            root.add(e.getKey(), o);
        }
        Files.writeString(path, GSON.toJson(root));
    }

    private static int intOr(JsonObject o, String key, int d) {
        return o.has(key) ? o.get(key).getAsInt() : d;
    }

    private static Map<String, LayoutRect> defaults(int width, int height) {
        int cx = width / 2;
        int baseX = cx - 380;
        int baseY = 24;
        Map<String, LayoutRect> m = new LinkedHashMap<>();
        m.put("VillageList", new LayoutRect(baseX + 8, baseY + 8, 160, 210));
        m.put("VillagerList", new LayoutRect(baseX + 180, baseY + 8, 180, 210));
        m.put("ProfilePanel", new LayoutRect(baseX + 8, baseY + 224, 460, 92));
        m.put("MapWidget", new LayoutRect(cx - 200 + 160, 36, 150, 130));
        m.put("ActionButtons", new LayoutRect(cx - 276, height - 112, 270, 110));
        return m;
    }

    public static class LayoutRect {
        public int x;
        public int y;
        public int w;
        public int h;

        public LayoutRect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public void setPos(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public void setSize(int w, int h) {
            this.w = Math.max(24, w);
            this.h = Math.max(24, h);
        }

        public boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }
}
