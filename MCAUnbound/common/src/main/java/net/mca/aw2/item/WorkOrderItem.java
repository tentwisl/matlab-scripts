package net.mca.aw2.item;

import net.mca.aw2.worker.WorkerManager;
import net.mca.aw2.worksite.WorksiteBlockEntity;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Work Order item. Used to assign MCA villagers to AW2 worksites.
 *
 * Usage:
 * 1. Right-click a worksite block to bind the work order to that worksite
 * 2. Right-click an MCA villager to assign them to the bound worksite
 *
 * This is the primary mechanism for integrating MCA villagers with AW2 worksites.
 */
public class WorkOrderItem extends Item {
    public WorkOrderItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();

        if (world.isClient() || player == null) return ActionResult.PASS;

        if (world.getBlockEntity(pos) instanceof WorksiteBlockEntity worksite) {
            ItemStack stack = context.getStack();
            NbtCompound nbt = stack.getOrCreateNbt();
            nbt.putInt("WsX", pos.getX());
            nbt.putInt("WsY", pos.getY());
            nbt.putInt("WsZ", pos.getZ());
            nbt.putString("WsType", worksite.getWorksiteType().getDisplayName());

            player.sendMessage(Text.literal(String.format(
                    "§6[Work Order]§r Bound to %s at (%d, %d, %d). Right-click a villager to assign them.",
                    worksite.getWorksiteType().getDisplayName(), pos.getX(), pos.getY(), pos.getZ()
            )), true);

            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (user.getWorld().isClient()) return ActionResult.PASS;

        if (entity instanceof VillagerEntityMCA villager) {
            NbtCompound nbt = stack.getNbt();
            if (nbt == null || !nbt.contains("WsX")) {
                user.sendMessage(Text.literal(
                        "§c[Work Order]§r Not bound to a worksite. Right-click a worksite first."
                ), true);
                return ActionResult.FAIL;
            }

            BlockPos worksitePos = new BlockPos(nbt.getInt("WsX"), nbt.getInt("WsY"), nbt.getInt("WsZ"));
            String wsType = nbt.getString("WsType");

            if (user.getWorld() instanceof ServerWorld serverWorld) {
                WorkerManager manager = WorkerManager.get(serverWorld);
                String villagerName = villager.getName().getString();
                boolean assigned = manager.assignVillagerToWorksite(
                        villager.getUuid(), villagerName, worksitePos, serverWorld);

                if (assigned) {
                    user.sendMessage(Text.literal(String.format(
                            "§a[Work Order]§r %s has been assigned to %s at (%d, %d, %d)!",
                            villagerName, wsType,
                            worksitePos.getX(), worksitePos.getY(), worksitePos.getZ()
                    )), true);
                } else {
                    user.sendMessage(Text.literal(String.format(
                            "§c[Work Order]§r Could not assign %s — worksite may be full or missing.",
                            villagerName
                    )), true);
                }
            }

            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.contains("WsX")) {
            tooltip.add(Text.literal(String.format("Bound to: %s at (%d, %d, %d)",
                    nbt.getString("WsType"),
                    nbt.getInt("WsX"), nbt.getInt("WsY"), nbt.getInt("WsZ")))
                    .formatted(Formatting.GOLD));
            tooltip.add(Text.literal("Right-click a villager to assign")
                    .formatted(Formatting.GRAY));
        } else {
            tooltip.add(Text.literal("Right-click a worksite to bind")
                    .formatted(Formatting.GRAY));
        }
    }
}
