package net.mca.aw2.item;

import net.mca.aw2.research.ResearchManager;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Research Book item. Stores which researches the owning player has completed.
 * Required at Engineering Stations and Auto Crafting stations to gate
 * which recipes can be crafted.
 *
 * Ported from AW2's ItemResearchBook.
 */
public class ResearchBookItem extends Item {
    public ResearchBookItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient() && world instanceof ServerWorld serverWorld) {
            // Sync research data to book
            NbtCompound nbt = stack.getOrCreateNbt();
            nbt.putUuid("ResearcherUuid", user.getUuid());
            nbt.putString("ResearcherName", user.getName().getString());

            ResearchManager manager = ResearchManager.get(serverWorld);
            Set<Identifier> completed = manager.getCompletedResearch(user.getUuid());

            StringBuilder sb = new StringBuilder();
            for (Identifier id : completed) {
                if (sb.length() > 0) sb.append(",");
                sb.append(id.toString());
            }
            nbt.putString("CompletedResearch", sb.toString());
            nbt.putInt("ScienceLevel", manager.calculateScienceLevel(user.getUuid()));

            user.sendMessage(Text.literal(String.format(
                    "§6[Research Book]§r Updated! %d researches recorded. Science Level: %d",
                    completed.size(), manager.calculateScienceLevel(user.getUuid())
            )), true);
        }

        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.contains("ResearcherName")) {
            tooltip.add(Text.literal("Owner: " + nbt.getString("ResearcherName"))
                    .formatted(Formatting.GRAY));
            tooltip.add(Text.literal("Science Level: " + nbt.getInt("ScienceLevel"))
                    .formatted(Formatting.AQUA));

            String completed = nbt.getString("CompletedResearch");
            if (!completed.isEmpty()) {
                int count = completed.split(",").length;
                tooltip.add(Text.literal(count + " researches completed")
                        .formatted(Formatting.GREEN));
            }
        } else {
            tooltip.add(Text.literal("Right-click to bind to your research")
                    .formatted(Formatting.GRAY));
        }
    }

    /**
     * Gets the researcher's name from the book's NBT.
     */
    @Nullable
    public static String getResearcherName(ItemStack stack) {
        if (stack.getItem() instanceof ResearchBookItem && stack.hasNbt()) {
            return stack.getNbt().getString("ResearcherName");
        }
        return null;
    }

    /**
     * Checks if a specific research is recorded in this book.
     */
    public static boolean hasResearch(ItemStack stack, Identifier researchId) {
        if (stack.getItem() instanceof ResearchBookItem && stack.hasNbt()) {
            String completed = stack.getNbt().getString("CompletedResearch");
            return completed.contains(researchId.toString());
        }
        return false;
    }
}
