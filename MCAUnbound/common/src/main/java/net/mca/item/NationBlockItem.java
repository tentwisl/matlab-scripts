package net.mca.item;

import net.minecraft.block.Block;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * Item form of the Nation Block.
 */
public class NationBlockItem extends BlockItem {

    public NationBlockItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.mca.nation_block.tooltip")
                .formatted(Formatting.GOLD));
        tooltip.add(Text.literal("Right-click placed block to open Nation menu")
                .formatted(Formatting.GRAY));
    }
}
