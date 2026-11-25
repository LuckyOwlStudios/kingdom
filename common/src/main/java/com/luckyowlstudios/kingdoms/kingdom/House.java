package com.luckyowlstudios.kingdoms.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A House Marker somewhere in the world, and what its last scan found (see {@link HouseScan}). A house belongs to the
 * kingdom whose land its marker is on. Each bed in a valid house is a home for one of that kingdom's villagers.
 * Saved with the kingdoms in {@link KingdomData}; change it through there.
 */
public final class House {

    public static final Codec<House> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GlobalPos.CODEC.fieldOf("pos").forGetter(House::pos),
            Codec.BOOL.optionalFieldOf("valid", false).forGetter(House::valid),
            BlockPos.CODEC.listOf().optionalFieldOf("beds", List.of()).forGetter(House::beds),
            UUIDUtil.CODEC.listOf().optionalFieldOf("occupants", List.of()).forGetter(House::occupants)
    ).apply(instance, House::new));

    private final GlobalPos pos;
    private boolean valid;
    private final List<BlockPos> beds = new ArrayList<>();
    private final List<UUID> occupants = new ArrayList<>();

    House(GlobalPos pos, boolean valid, List<BlockPos> beds, List<UUID> occupants) {
        this.pos = pos;
        this.valid = valid;
        this.beds.addAll(beds);
        this.occupants.addAll(occupants);
    }

    /** Where the House Marker is. */
    public GlobalPos pos() {
        return this.pos;
    }

    /** Whether the last scan found a proper home, on a kingdom's land. */
    public boolean valid() {
        return this.valid;
    }

    /** The head of each bed in the house, as of the last scan that found it closed in. */
    public List<BlockPos> beds() {
        return Collections.unmodifiableList(this.beds);
    }

    /** The villagers who live here, by entity UUID. The first sleeps in the first bed, and so on. */
    public List<UUID> occupants() {
        return Collections.unmodifiableList(this.occupants);
    }

    /** How many villagers can live here: one per bed, or none while the house isn't valid. */
    public int homes() {
        return this.valid ? this.beds.size() : 0;
    }

    /** A valid house with a bed nobody has. */
    public boolean hasRoom() {
        return this.occupants.size() < this.homes();
    }

    // ---- Changes, used by KingdomData ----

    void setValid(boolean valid) {
        this.valid = valid;
    }

    List<BlockPos> mutableBeds() {
        return this.beds;
    }

    List<UUID> mutableOccupants() {
        return this.occupants;
    }
}
