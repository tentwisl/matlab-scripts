package net.mca.client.gui;

import net.mca.MCA;
import net.mca.server.world.data.politics.PoliticalCompass;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight nation management/debug UI for simulation analytics.
 */
public class NationManagementScreen extends ExtendedScreen {
    private final List<Row> rows = new ArrayList<>();
    private int selected = 0;

    public NationManagementScreen() {
        super(Text.literal("Nation Management"));
        // placeholder resident analytics rows
        rows.add(new Row("Resident A", 42, 0.62, new PoliticalCompass(0.7, 0.2, 0.6, 0.3)));
        rows.add(new Row("Resident B", 58, 0.10, new PoliticalCompass(0.2, 0.6, 0.4, 0.7)));
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("- LFP"), b -> adjustLfp(-1)).dimensions(cx - 130, height - 44, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+ LFP"), b -> adjustLfp(1)).dimensions(cx - 64, height - 44, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("- NFP"), b -> adjustNfp(-0.05)).dimensions(cx + 2, height - 44, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+ NFP"), b -> adjustNfp(0.05)).dimensions(cx + 68, height - 44, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Skip Election Timer"), b -> logDecisionTrace()).dimensions(cx - 80, height - 22, 160, 20).build());
    }

    private void adjustLfp(int delta) {
        Row row = rows.get(Math.max(0, Math.min(selected, rows.size() - 1)));
        row.lfp = Math.max(0, Math.min(100, row.lfp + delta));
    }

    private void adjustNfp(double delta) {
        Row row = rows.get(Math.max(0, Math.min(selected, rows.size() - 1)));
        row.nfp = Math.max(-1.0, Math.min(1.0, row.nfp + delta));
    }

    private void logDecisionTrace() {
        MCA.LOGGER.info("[NationSim] ---- Voting Decision Trace ----");
        for (Row row : rows) {
            MCA.LOGGER.info("[NationSim] {} -> LFP={}, NFP={}, Compass[N={},C={},A={},L={}]",
                    row.name, row.lfp, row.nfp,
                    row.compass.nationalist(), row.compass.communist(), row.compass.authoritarian(), row.compass.libertarian());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int y = 48;
        for (int i = 0; i < rows.size(); i++) {
            if (mouseY >= y && mouseY <= y + 12) {
                selected = i;
                return true;
            }
            y += 14;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        int cx = width / 2;
        context.drawCenteredTextWithShadow(textRenderer, "Simulation Analytics", cx, 20, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "Name", cx - 150, 34, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, "LFP", cx - 20, 34, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, "NFP", cx + 20, 34, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, "Compass [N,C,A,L]", cx + 60, 34, 0xAAAAAA);

        int y = 48;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int color = i == selected ? 0xFFFFFF : 0xCCCCCC;
            context.drawTextWithShadow(textRenderer, row.name, cx - 150, y, color);
            context.drawTextWithShadow(textRenderer, Integer.toString(row.lfp), cx - 20, y, color);
            context.drawTextWithShadow(textRenderer, String.format("%.2f", row.nfp), cx + 20, y, color);
            context.drawTextWithShadow(textRenderer,
                    String.format("[%.2f, %.2f, %.2f, %.2f]", row.compass.nationalist(), row.compass.communist(), row.compass.authoritarian(), row.compass.libertarian()),
                    cx + 60, y, color);
            y += 14;
        }
    }

    private static class Row {
        private final String name;
        private int lfp;
        private double nfp;
        private final PoliticalCompass compass;

        private Row(String name, int lfp, double nfp, PoliticalCompass compass) {
            this.name = name;
            this.lfp = lfp;
            this.nfp = nfp;
            this.compass = compass;
        }
    }
}
