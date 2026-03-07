package net.mca.aw2.item;

import net.mca.aw2.worksite.WorksiteBlockEntity;
import net.mca.aw2.worksite.WorksiteUpgrade;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Worksite upgrade item. Right-click a worksite to apply the upgrade.
 * Ported from AW2's ItemWorksiteUpgrade.
 */
public class WorksiteUpgradeItem extends Item {
    private final WorksiteUpgrade upgradeType;

    public WorksiteUpgradeItem(Settings settings, WorksiteUpgrade upgradeType) {
        super(settings);
        this.upgradeType = upgradeType;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();

        if (world.isClient() || player == null) return ActionResult.PASS;

        if (world.getBlockEntity(pos) instanceof WorksiteBlockEntity worksite) {
            if (worksite.hasUpgrade(upgradeType)) {
                player.sendMessage(Text.literal(
                        "§c[Upgrade]§r This worksite already has " + upgradeType.getDisplayName()
                ), true);
                return ActionResult.FAIL;
            }

            if (worksite.addUpgrade(upgradeType)) {
                context.getStack().decrement(1);
                player.sendMessage(Text.literal(
                        "§a[Upgrade]§r Applied " + upgradeType.getDisplayName() + "!"
                ), true);
                return ActionResult.SUCCESS;
            }
        }

        return ActionResult.PASS;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal(upgradeType.getDisplayName())
                .formatted(Formatting.GOLD));
        tooltip.add(Text.literal(upgradeType.getDescription())
                .formatted(Formatting.GRAY));
    }

    public WorksiteUpgrade getUpgradeType() {
        return upgradeType;
    }
}
