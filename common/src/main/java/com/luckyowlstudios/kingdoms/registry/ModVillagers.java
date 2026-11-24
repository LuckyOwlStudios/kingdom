package com.luckyowlstudios.kingdoms.registry;

import com.google.common.collect.ImmutableSet;
import com.luckyowlstudios.kingdoms.Constants;
import com.luckyowlstudios.kingdoms.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.function.Supplier;

/**
 * The kingdom's work blocks as villager job sites, and the professions that work at them: the Town Hall and its
 * Advisor, the Scholar's Desk and its Scholar, and the Watchtower and its Lookout.
 * <p>
 * Each is a point of interest with one ticket, so one villager can claim each block, and an unemployed villager that
 * claims one takes its job.
 */
public final class ModVillagers {

    public static final String TOWN_HALL = "town_hall";
    public static final String ADVISOR = "advisor";
    public static final String SCHOLARS_DESK = "scholars_desk";
    public static final String SCHOLAR = "scholar";
    public static final String WATCHTOWER = "watchtower";
    public static final String LOOKOUT = "lookout";

    public static final ResourceKey<PoiType> TOWN_HALL_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Constants.id(TOWN_HALL));
    public static final ResourceKey<PoiType> SCHOLARS_DESK_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Constants.id(SCHOLARS_DESK));
    public static final ResourceKey<PoiType> WATCHTOWER_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Constants.id(WATCHTOWER));

    /** Like vanilla job sites: one villager per block, and it has to stand next to it. */
    public static final int TOWN_HALL_TICKETS = 1;
    public static final int TOWN_HALL_RANGE = 1;

    public static final Supplier<PoiType> TOWN_HALL_POI_TYPE = Services.REGISTRY.poiType(TOWN_HALL, TOWN_HALL_TICKETS, TOWN_HALL_RANGE, ModBlocks.TOWN_HALL);

    public static final Supplier<VillagerProfession> ADVISOR_PROFESSION = Services.REGISTRY.register(BuiltInRegistries.VILLAGER_PROFESSION, ADVISOR,
            () -> new VillagerProfession(ADVISOR,
                    poi -> poi.is(TOWN_HALL_POI),
                    poi -> poi.is(TOWN_HALL_POI),
                    ImmutableSet.of(), ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_CARTOGRAPHER));

    public static final Supplier<PoiType> SCHOLARS_DESK_POI_TYPE = Services.REGISTRY.poiType(SCHOLARS_DESK, TOWN_HALL_TICKETS, TOWN_HALL_RANGE, ModBlocks.SCHOLARS_DESK);

    /** Works at a Scholar's Desk. Scholars living in a kingdom research its perks (see KingdomResearch). */
    public static final Supplier<VillagerProfession> SCHOLAR_PROFESSION = Services.REGISTRY.register(BuiltInRegistries.VILLAGER_PROFESSION, SCHOLAR,
            () -> new VillagerProfession(SCHOLAR,
                    poi -> poi.is(SCHOLARS_DESK_POI),
                    poi -> poi.is(SCHOLARS_DESK_POI),
                    ImmutableSet.of(), ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_LIBRARIAN));

    public static final Supplier<PoiType> WATCHTOWER_POI_TYPE = Services.REGISTRY.poiType(WATCHTOWER, TOWN_HALL_TICKETS, TOWN_HALL_RANGE, ModBlocks.WATCHTOWER);

    /** Works at a Watchtower. Lookouts living in a kingdom warn it about what they see (see Watchtowers). */
    public static final Supplier<VillagerProfession> LOOKOUT_PROFESSION = Services.REGISTRY.register(BuiltInRegistries.VILLAGER_PROFESSION, LOOKOUT,
            () -> new VillagerProfession(LOOKOUT,
                    poi -> poi.is(WATCHTOWER_POI),
                    poi -> poi.is(WATCHTOWER_POI),
                    ImmutableSet.of(), ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_FLETCHER));

    private ModVillagers() {
    }

    static void init() {
    }
}
