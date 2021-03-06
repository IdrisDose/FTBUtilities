package com.feed_the_beast.ftbu.handlers;

import com.feed_the_beast.ftbl.api.IForgePlayer;
import com.feed_the_beast.ftbl.api.IForgeTeam;
import com.feed_the_beast.ftbl.api.config.IConfigValue;
import com.feed_the_beast.ftbl.api.events.player.ForgePlayerDeathEvent;
import com.feed_the_beast.ftbl.api.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftbl.api.events.player.ForgePlayerLoggedOutEvent;
import com.feed_the_beast.ftbl.api.events.player.ForgePlayerSettingsEvent;
import com.feed_the_beast.ftbl.lib.config.PropertyItemStack;
import com.feed_the_beast.ftbl.lib.config.PropertyTextComponent;
import com.feed_the_beast.ftbl.lib.math.ChunkDimPos;
import com.feed_the_beast.ftbl.lib.math.EntityDimPos;
import com.feed_the_beast.ftbl.lib.util.LMInvUtils;
import com.feed_the_beast.ftbu.FTBLibIntegration;
import com.feed_the_beast.ftbu.FTBUNotifications;
import com.feed_the_beast.ftbu.api.chunks.BlockInteractionType;
import com.feed_the_beast.ftbu.api_impl.ClaimedChunkStorage;
import com.feed_the_beast.ftbu.api_impl.LoadedChunkStorage;
import com.feed_the_beast.ftbu.config.FTBUConfigLogin;
import com.feed_the_beast.ftbu.config.FTBUConfigWorld;
import com.feed_the_beast.ftbu.net.MessageSendFTBUClientFlags;
import com.feed_the_beast.ftbu.world.FTBUPlayerData;
import com.feed_the_beast.ftbu.world.FTBUUniverseData;
import com.google.common.base.Objects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FTBUPlayerEventHandler
{
    @SubscribeEvent
    public void onLoggedIn(ForgePlayerLoggedInEvent event)
    {
        EntityPlayerMP ep = event.getPlayer().getPlayer();

        if(event.isFirstLogin())
        {
            if(FTBUConfigLogin.ENABLE_STARTING_ITEMS.getBoolean())
            {
                for(IConfigValue value : FTBUConfigLogin.STARTING_ITEMS)
                {
                    LMInvUtils.giveItem(ep, ((PropertyItemStack) value).getItem());
                }
            }
        }

        if(FTBUConfigLogin.ENABLE_MOTD.getBoolean())
        {
            for(IConfigValue value : FTBUConfigLogin.MOTD)
            {
                ITextComponent t = ((PropertyTextComponent) value).getText();

                if(t != null)
                {
                    ep.addChatMessage(t);
                }
            }
        }

        LoadedChunkStorage.INSTANCE.checkAll();

        Map<UUID, Integer> map = new HashMap<>(1);
        int flags = FTBUPlayerData.get(event.getPlayer()).getClientFlags();
        map.put(ep.getGameProfile().getId(), flags);
        new MessageSendFTBUClientFlags(map).sendTo(null);
        map.clear();

        for(IForgePlayer player : FTBLibIntegration.API.getUniverse().getOnlinePlayers())
        {
            FTBUPlayerData data = FTBUPlayerData.get(player);

            if(data != null)
            {
                map.put(player.getId(), data.getClientFlags());
            }
        }

        new MessageSendFTBUClientFlags(map).sendTo(ep);
    }

    @SubscribeEvent
    public void onLoggedOut(ForgePlayerLoggedOutEvent event)
    {
        LoadedChunkStorage.INSTANCE.checkAll();
    }

    @SubscribeEvent
    public void onDeath(ForgePlayerDeathEvent event)
    {
        FTBUPlayerData data = FTBUPlayerData.get(event.getPlayer());
        if(data != null)
        {
            data.lastDeath = new EntityDimPos(event.getPlayer().getPlayer()).toBlockDimPos();
        }
    }

    @SubscribeEvent
    public void getSettings(ForgePlayerSettingsEvent event)
    {
        FTBUPlayerData data = FTBUPlayerData.get(event.getPlayer());
        if(data != null)
        {
            data.addConfig(event.getSettings());
        }
    }
    
    /*
    @SubscribeEvent
    public void addInfo(ForgePlayerInfoEvent event)
    {
        if(owner.getRank().config.show_rank.getMode())
		{
		    Rank rank = getRank();
		    IChatComponent rankC = new ChatComponentText("[" + rank.ID + "]");
		    rankC.getChatStyle().setColor(rank.color.getMode());
		    info.add(rankC);
		}
    }
    */

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onChunkChanged(EntityEvent.EnteringChunk e)
    {
        if(e.getEntity().worldObj.isRemote || !(e.getEntity() instanceof EntityPlayerMP))
        {
            return;
        }

        EntityPlayerMP ep = (EntityPlayerMP) e.getEntity();
        IForgePlayer player = FTBLibIntegration.API.getUniverse().getPlayer(ep);

        if(player == null || !player.isOnline())
        {
            return;
        }

        FTBUPlayerData data = FTBUPlayerData.get(player);

        if(data != null)
        {
            data.lastSafePos = new EntityDimPos(ep).toBlockDimPos();
        }

        updateChunkMessage(ep, new ChunkDimPos(e.getNewChunkX(), e.getNewChunkZ(), ep.dimension));
    }

    public static void updateChunkMessage(EntityPlayerMP player, ChunkDimPos pos)
    {
        IForgePlayer newTeamOwner = ClaimedChunkStorage.INSTANCE.getChunkOwner(pos);

        FTBUPlayerData data = FTBUPlayerData.get(FTBLibIntegration.API.getUniverse().getPlayer(player));

        if(data == null)
        {
            return;
        }

        if(!Objects.equal(data.lastChunkOwner, newTeamOwner))
        {
            data.lastChunkOwner = newTeamOwner;

            if(newTeamOwner != null)
            {
                IForgeTeam team = newTeamOwner.getTeam();

                if(team != null)
                {
                    FTBLibIntegration.API.sendNotification(player, FTBUNotifications.chunkChanged(team));
                }
            }
            else
            {
                FTBLibIntegration.API.sendNotification(player, FTBUNotifications.chunkChanged(null));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlayerAttacked(LivingAttackEvent e)
    {
        if(e.getEntity().worldObj.isRemote)
        {
            return;
        }

        if(e.getEntity().dimension != 0 || !(e.getEntity() instanceof EntityPlayerMP) || e.getEntity() instanceof FakePlayer)
        {
            return;
        }

        Entity entity = e.getSource().getSourceOfDamage();

        if(entity != null && (entity instanceof EntityPlayerMP || entity instanceof IMob))
        {
            if(entity instanceof FakePlayer)
            {
                return;
            }
            /*else if(entity instanceof EntityPlayerMP && PermissionAPI.hasPermission(((EntityPlayerMP) entity).getGameProfile(), FTBLibPermissions.INTERACT_SECURE, false, new Context(entity)))
            {
                return;
            }*/

            if((FTBUConfigWorld.SAFE_SPAWN.getBoolean() && FTBUUniverseData.isInSpawnD(e.getEntity().dimension, e.getEntity().posX, e.getEntity().posZ)))
            {
                e.setCanceled(true);
            }
            /*else
            {
				ClaimedChunk c = Claims.getMode(dim, cx, cz);
				if(c != null && c.claims.settings.isSafe()) e.setCanceled(true);
			}*/
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        if(event.getEntityPlayer() instanceof EntityPlayerMP && !ClaimedChunkStorage.INSTANCE.canPlayerInteract((EntityPlayerMP) event.getEntityPlayer(), event.getHand(), event.getPos(), event.getFace(), BlockInteractionType.RIGHT_CLICK))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        if(event.getEntityPlayer() instanceof EntityPlayerMP && !ClaimedChunkStorage.INSTANCE.canPlayerInteract((EntityPlayerMP) event.getEntityPlayer(), event.getHand(), event.getPos(), event.getFace(), BlockInteractionType.ITEM))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockEvent.BreakEvent event)
    {
        if(event.getPlayer() instanceof EntityPlayerMP && !ClaimedChunkStorage.INSTANCE.canPlayerInteract((EntityPlayerMP) event.getPlayer(), EnumHand.MAIN_HAND, event.getPos(), null, BlockInteractionType.BREAK))
        {
            event.setCanceled(true);
        }
    }

    @Optional.Method(modid = "chiselsandbits")
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onChiselEvent(mod.chiselsandbits.api.EventBlockBitModification event)
    {
        if(event.getPlayer() instanceof EntityPlayerMP && !ClaimedChunkStorage.INSTANCE.canPlayerInteract((EntityPlayerMP) event.getPlayer(), event.getHand(), event.getPos(), null, event.isPlacing() ? BlockInteractionType.CNB_PLACE : BlockInteractionType.CNB_BREAK))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBlockLeftClick(PlayerInteractEvent.LeftClickBlock event)
    {
        if(event.getEntityPlayer() instanceof EntityPlayerMP && !ClaimedChunkStorage.INSTANCE.canPlayerInteract((EntityPlayerMP) event.getEntityPlayer(), event.getHand(), event.getPos(), event.getFace(), BlockInteractionType.BREAK))
        {
            event.setCanceled(true);
        }
    }

    /*
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onItemPickup(EntityItemPickupEvent event)
    {
    }

    @Optional.Method(modid = "iChunUtil") //TODO: Change to lowercase whenever iChun does
    @SubscribeEvent
    public void onBlockPickupEventEvent(me.ichun.mods.ichunutil.api.event.BlockPickupEvent event)
    {
    }
    */
}