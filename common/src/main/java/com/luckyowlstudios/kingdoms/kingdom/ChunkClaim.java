package com.luckyowlstudios.kingdoms.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * One chunk of land belonging to a kingdom. A kingdom can hold any number of these; for now it gets the chunk its
 * Town Hall stands in.
 */
public record ChunkClaim(ResourceKey<Level> dimension, int x, int z) {

    public static final Codec<ChunkClaim> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ChunkClaim::dimension),
            Codec.INT.fieldOf("x").forGetter(ChunkClaim::x),
            Codec.INT.fieldOf("z").forGetter(ChunkClaim::z)
    ).apply(instance, ChunkClaim::new));

    public static ChunkClaim of(ResourceKey<Level> dimension, ChunkPos pos) {
        return new ChunkClaim(dimension, pos.x, pos.z);
    }

    /** The chunk a block is in. */
    public static ChunkClaim of(GlobalPos pos) {
        return of(pos.dimension(), new ChunkPos(pos.pos()));
    }

    public ChunkPos chunkPos() {
        return new ChunkPos(this.x, this.z);
    }
}
