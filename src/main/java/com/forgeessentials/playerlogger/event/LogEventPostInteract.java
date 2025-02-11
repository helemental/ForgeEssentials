package com.forgeessentials.playerlogger.event;

import java.util.Date;

import javax.persistence.EntityManager;

import net.minecraftforge.fe.event.player.PlayerPostInteractEvent;

import com.forgeessentials.playerlogger.PlayerLoggerEvent;
import com.forgeessentials.playerlogger.entity.ActionBlock;
import com.forgeessentials.playerlogger.entity.ActionBlock.ActionBlockType;

public class LogEventPostInteract extends PlayerLoggerEvent<PlayerPostInteractEvent>
{

    public LogEventPostInteract(PlayerPostInteractEvent event)
    {
        super(event);
    }

    @Override
    public void process(EntityManager em)
    {
        ActionBlock action = new ActionBlock();
        action.time = new Date();
        action.player = getPlayer(event.entityPlayer.getPersistentID());
        action.world = getWorld(event.world.provider.dimensionId);
        // action.block = getBlock(block);
        // action.metadata = metadata;
        action.type = ActionBlockType.USE_RIGHT;
        action.x = event.x;
        action.y = event.y;
        action.z = event.z;
        em.persist(action);
    }
    
}