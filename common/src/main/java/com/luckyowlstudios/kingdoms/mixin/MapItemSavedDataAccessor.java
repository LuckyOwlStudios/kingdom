package com.luckyowlstudios.kingdoms.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Vanilla only makes maps through {@link MapItemSavedData#createFresh}, which snaps the centre to its world-wide map
 * grid. Kingdom maps need to be centred exactly on the Town Hall, so they use the private constructor instead.
 */
@Mixin(MapItemSavedData.class)
public interface MapItemSavedDataAccessor {

    @Invoker("<init>")
    static MapItemSavedData kingdoms$create(int x, int z, byte scale, boolean trackingPosition, boolean unlimitedTracking,
                                            boolean locked, ResourceKey<Level> dimension) {
        throw new AssertionError();
    }
}
