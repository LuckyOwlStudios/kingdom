package com.luckyowlstudios.kingdoms.client;

import com.luckyowlstudios.kingdoms.block.entity.TownHallBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Block colour for the Town Hall: its dyeable parts (the faces with {@code tintindex} 0 in its model) take the
 * colour of the kingdom seated there. A hall with no kingdom, and the item, keep the texture's own grey.
 */
public final class TownHallTint {

    private static final int UNTINTED = 0xFFFFFFFF;

    private TownHallTint() {
    }

    public static int getColor(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        if (tintIndex != 0 || level == null || pos == null) {
            return UNTINTED;
        }
        if (level.getBlockEntity(pos) instanceof TownHallBlockEntity hall) {
            DyeColor color = hall.kingdomColor();
            if (color != null) {
                return color.getTextureDiffuseColor();
            }
        }
        return UNTINTED;
    }
}
