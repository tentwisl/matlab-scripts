package net.mca.client.gui;

import net.mca.nation.CityData;
import net.mca.network.s2c.TownHallDataResponse;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

/**
 * Town Hall block GUI — shows village info, city tier, current nation affiliation,
 * player reputation, and independence score.
 */
public class TownHallScreen extends ExtendedScreen {

    private String villageName = "Unknown";
    private CityData cityData;
    private String owningNationName = "Independent";
    private String owningNationGov  = "";
    private int playerReputation    = 0;

    public TownHallScreen() {
        super(Text.translatable("gui.mca.town_hall.title"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);

        int cx = width / 2;
        int y  = 20;

        ctx.drawCenteredTextWithShadow(textRenderer, "Town Hall — " + villageName, cx, y, 0xFFFFAA);
        y += 18;

        if (cityData != null) {
            ctx.drawTextWithShadow(textRenderer, "City Tier:    " + cityData.getTier().name(), cx - 100, y, 0xCCCCCC);      y += 12;
            ctx.drawTextWithShadow(textRenderer, "Nation:       " + owningNationName, cx - 100, y, 0xCCCCCC);               y += 12;
            if (!owningNationGov.isEmpty()) {
                ctx.drawTextWithShadow(textRenderer, "Government:   " + owningNationGov, cx - 100, y, 0xCCCCCC);            y += 12;
            }
            ctx.drawTextWithShadow(textRenderer, "Independence: " + cityData.getIndependenceScore() + "/100", cx - 100, y, 0xCCCCCC);  y += 12;
            ctx.drawTextWithShadow(textRenderer, "Your Rep:     " + playerReputation, cx - 100, y, playerReputation >= 0 ? 0xAAFFAA : 0xFF8888); y += 12;
            ctx.drawTextWithShadow(textRenderer, "Base Opinion: " + cityData.getBaseOpinion(), cx - 100, y, 0xCCCCCC);      y += 18;

            ctx.drawCenteredTextWithShadow(textRenderer, "-- Market (Coming Soon) --", cx, y, 0x888888);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer, "Loading...", cx, y, 0x888888);
        }
    }

    public void loadData(TownHallDataResponse response) {
        NbtCompound root = response.getData();
        villageName = root.getString("villageName");
        if (villageName.isEmpty()) villageName = "Unknown";

        if (root.contains("city")) {
            cityData = new CityData(root.getCompound("city"));
        }

        if (root.contains("owningNation")) {
            NbtCompound ns = root.getCompound("owningNation");
            owningNationName = ns.getString("name");
            owningNationGov  = ns.getString("gov");
        } else {
            owningNationName = "Independent";
        }

        playerReputation = root.getInt("playerReputation");
    }

    @Override
    public boolean shouldPause() { return false; }
}
