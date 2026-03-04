package net.mca.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Nation Block — received by a player after successfully forming a nation
 * from their Town Hall.  Represents the founding charter / nation seat that
 * will be developed further in later iterations.
 */
public class NationBlockItem extends Item {

    public NationBlockItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.mca.nation_block.tooltip")
                .formatted(Formatting.GOLD));
        tooltip.add(Text.literal("Use while sneaking to open Nation analytics (WIP)")
                .formatted(Formatting.GRAY));
    }
}
