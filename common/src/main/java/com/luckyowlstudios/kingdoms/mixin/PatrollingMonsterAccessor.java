package com.luckyowlstudios.kingdoms.mixin;

import net.minecraft.world.entity.monster.PatrollingMonster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the Watchtower tell a patrolling illager from one that's guarding an outpost or raiding. */
@Mixin(PatrollingMonster.class)
public interface PatrollingMonsterAccessor {

    @Invoker("isPatrolling")
    boolean kingdoms$isPatrolling();
}
