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
 * The Town Hall as a villager job site, and the Advisor, the profession that works at it.
 * <p>
 * The Town Hall is a point of interest with one ticket, so one villager can claim each Town Hall, and an
 * unemployed villager that claims it becomes the Advisor.
 */
public final class ModVillagers {

    public static final String TOWN_HALL = "town_hall";
    public static final String ADVISOR = "advisor";

    public static final ResourceKey<PoiType> TOWN_HALL_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Constants.id(TOWN_HALL));

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

    public static final String TAVERN = "tavern";
    public static final String INNKEEPER = "innkeeper";
    public static final ResourceKey<PoiType> TAVERN_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Constants.id(TAVERN));
    public static final Supplier<PoiType> TAVERN_POI_TYPE = Services.REGISTRY.poiType(TAVERN, TOWN_HALL_TICKETS, TOWN_HALL_RANGE, ModBlocks.TAVERN);

    /** Works at a Tavern. An Innkeeper living in a kingdom lets a villager move in at night too (see KingdomTavern). */
    public static final Supplier<VillagerProfession> INNKEEPER_PROFESSION = Services.REGISTRY.register(BuiltInRegistries.VILLAGER_PROFESSION, INNKEEPER,
            () -> new VillagerProfession(INNKEEPER,
                    poi -> poi.is(TAVERN_POI),
                    poi -> poi.is(TAVERN_POI),
                    ImmutableSet.of(), ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_BUTCHER));

    private ModVillagers() {
    }

    static void init() {
    }
}
