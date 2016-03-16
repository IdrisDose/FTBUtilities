package ftb.utils.world;

import com.google.gson.JsonArray;
import ftb.lib.*;
import ftb.lib.api.ForgePlayerMP;
import ftb.lib.api.item.LMInvUtils;
import ftb.utils.*;
import ftb.utils.badges.ServerBadges;
import ftb.utils.config.FTBUConfigLogin;
import ftb.utils.handlers.FTBUChunkEventHandler;
import ftb.utils.net.MessageAreaUpdate;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

/**
 * Created by LatvianModder on 23.02.2016.
 */
public class FTBUPlayerDataMP extends FTBUPlayerData
{
	public static FTBUPlayerDataMP get(ForgePlayerMP p)
	{ return (FTBUPlayerDataMP) p.getData(FTBUFinals.MOD_ID); }
	
	public Warps homes;
	public ChunkType lastChunkType;
	
	public FTBUPlayerDataMP(ForgePlayerMP p)
	{
		super(FTBUFinals.MOD_ID, p);
		homes = new Warps();
	}
	
	public void readFromServer(NBTTagCompound tag)
	{
		setFlag(RENDER_BADGE, tag.hasKey("Badge") ? tag.getBoolean("Badge") : true);
		setFlag(CHAT_LINKS, tag.hasKey("ChatLinks") ? tag.getBoolean("ChatLinks") : false);
		setFlag(EXPLOSIONS, tag.hasKey("Explosions") ? tag.getBoolean("Explosions") : true);
		setFlag(FAKE_PLAYERS, tag.hasKey("FakePlayers") ? tag.getBoolean("FakePlayers") : true);
		blocks = PrivacyLevel.VALUES_3[tag.getByte("BlockSecurity")];
		
		homes.readFromNBT(tag, "Homes");
	}
	
	public void writeToServer(NBTTagCompound tag)
	{
		tag.setBoolean("Badge", getFlag(RENDER_BADGE));
		tag.setBoolean("ChatLinks", getFlag(CHAT_LINKS));
		tag.setBoolean("Explosions", getFlag(EXPLOSIONS));
		tag.setBoolean("FakePlayers", getFlag(FAKE_PLAYERS));
		tag.setByte("BlockSecurity", (byte) blocks.ordinal());
		
		homes.writeToNBT(tag, "Homes");
	}
	
	public void writeToNet(NBTTagCompound tag, boolean self)
	{
		tag.setByte("F", flags);
		tag.setByte("B", (byte) blocks.ordinal());
		
		if(self)
		{
			tag.setInteger("CC", getClaimedChunks());
			tag.setInteger("LC", getLoadedChunks(true));
			tag.setInteger("MCC", getMaxClaimedChunks());
			tag.setInteger("MLC", getMaxLoadedChunks());
		}
	}
	
	public void onLoggedIn(boolean firstTime)
	{
		EntityPlayerMP ep = player.toPlayerMP().getPlayer();
		
		if(firstTime)
		{
			for(ItemStack is : FTBUConfigLogin.getStartingItems(player.getProfile().getId()))
			{
				LMInvUtils.giveItem(ep, is);
			}
		}
		
		FTBUConfigLogin.printMotd(ep);
		Backups.hadPlayer = true;
		ServerBadges.sendToPlayer(ep);
		
		new MessageAreaUpdate(player.toPlayerMP(), player.toPlayerMP().getPos(), 1).sendTo(ep);
		FTBUChunkEventHandler.instance.markDirty(null);
	}
	
	public void onLoggedOut()
	{
		//Backups.shouldRun = true;
		FTBUChunkEventHandler.instance.markDirty(null);
	}
	
	public void onDeath()
	{
	}
	
	public int getClaimedChunks()
	{ return FTBUWorldDataMP.get().getChunks(player.getProfile().getId(), null).size(); }
	
	public int getLoadedChunks(boolean forced)
	{
		int loaded = 0;
		for(ClaimedChunk c : FTBUWorldDataMP.get().getChunks(player.getProfile().getId(), null))
		{
			if(c.isChunkloaded && (!forced || c.isForced))
			{
				loaded++;
			}
		}
		
		return loaded;
	}
	
	public short getMaxClaimedChunks()
	{ return FTBUPermissions.claims_max_chunks.get(player.getProfile()).getAsShort(); }
	
	public short getMaxLoadedChunks()
	{ return FTBUPermissions.chunkloader_max_chunks.get(player.getProfile()).getAsShort(); }
	
	public boolean isDimensionBlacklisted(int dim)
	{
		JsonArray a = FTBUPermissions.claims_dimension_blacklist.get(player.getProfile()).getAsJsonArray();
		
		for(int i = 0; i < a.size(); i++)
		{
			if(a.get(i).getAsInt() == dim) return true;
		}
		
		return false;
	}
	
	public void claimChunk(ChunkDimPos pos)
	{
		if(isDimensionBlacklisted(pos.dim)) return;
		short max = getMaxClaimedChunks();
		if(max == 0) return;
		if(getClaimedChunks() >= max) return;
		
		ChunkType t = FTBUWorldDataMP.get().getType(player.toPlayerMP(), pos);
		if(t.asClaimed() == null && t.isChunkOwner(player.toPlayerMP()) && FTBUWorldDataMP.get().put(new ClaimedChunk(player.getProfile().getId(), pos)))
		{
			player.sendUpdate();
		}
	}
	
	public void unclaimChunk(ChunkDimPos pos)
	{
		if(FTBUWorldDataMP.get().getType(player.toPlayerMP(), pos).isChunkOwner(player.toPlayerMP()))
		{
			setLoaded(pos, false);
			FTBUWorldDataMP.get().remove(pos);
			player.sendUpdate();
		}
	}
	
	public void unclaimAllChunks(Integer dim)
	{
		List<ClaimedChunk> list = FTBUWorldDataMP.get().getChunks(player.getProfile().getId(), dim);
		int size0 = list.size();
		if(size0 == 0) return;
		
		for(ClaimedChunk c : list)
		{
			setLoaded(c.pos, false);
			FTBUWorldDataMP.get().remove(c.pos);
		}
		
		player.sendUpdate();
	}
	
	public void setLoaded(ChunkDimPos pos, boolean flag)
	{
		ClaimedChunk chunk = FTBUWorldDataMP.get().getChunk(pos);
		if(chunk == null) return;
		
		if(flag != chunk.isChunkloaded && player.equalsPlayer(chunk.getOwner()))
		{
			if(flag)
			{
				if(isDimensionBlacklisted(pos.dim)) return;
				short max = getMaxLoadedChunks();
				if(max == 0) return;
				if(getLoadedChunks(false) >= max) return;
			}
			
			chunk.isChunkloaded = flag;
			FTBUChunkEventHandler.instance.markDirty(LMDimUtils.getWorld(pos.dim));
			
			if(player.getPlayer() != null)
			{
				new MessageAreaUpdate(player.toPlayerMP(), pos.chunkXPos, pos.chunkZPos, pos.dim, 1, 1).sendTo(player.toPlayerMP().getPlayer());
				player.sendUpdate();
			}
		}
	}
}
