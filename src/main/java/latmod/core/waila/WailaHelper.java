package latmod.core.waila;
import net.minecraftforge.common.MinecraftForge;
import latmod.core.util.*;
import mcp.mobius.waila.api.IWailaDataProvider;
import cpw.mods.fml.common.eventhandler.Event;

public class WailaHelper
{
	private static Class<?> moduleRegistry;
	private static Object instance;
	
	private static boolean hasInited = false;
	private static boolean isInstalled = false;
	
	private static final FastMap<String, FastList<String>> configMap = new FastMap<String, FastList<String>>();
	
	public static void init()
	{
		if(hasInited) return; hasInited = true;
		
		try
		{
			moduleRegistry = Class.forName("mcp.mobius.waila.api.impl.ModuleRegistrar");
			instance = moduleRegistry.getMethod("instance").invoke(null);
			
			System.out.println("[LatCore] Waila found!");
			isInstalled = true;
		}
		catch(Exception e)
		{
			System.out.println("[LatCoreMC] Waila not installed!");
			isInstalled = false;
		}
		
		try
		{
		}
		catch(Exception e)
		{ e.printStackTrace(); }
	}
	
	public static boolean isInstalled()
	{ init(); return isInstalled; }
	
	public static void invokeRegistryMethod(String s, Class<?>[] c, Object[] o) throws Exception
	{ init(); moduleRegistry.getMethod(s, c).invoke(instance, o); }
	
	public static void registerConfig(String s, String s1) throws Exception
	{ invokeRegistryMethod("addConfig", new Class<?>[] { String.class, String.class }, new Object[] { s, s1 }); }
	
	public static void registerDataProvider(Class<?> block, BasicWailaHandler i) throws Exception
	{
		if(i.registerStack) invokeRegistryMethod("registerStackProvider",
				new Class<?>[] { IWailaDataProvider.class, Class.class },
				new Object[] { i, block });
		
		if(i.registerHead) invokeRegistryMethod("registerHeadProvider",
				new Class<?>[] { IWailaDataProvider.class, Class.class },
				new Object[] { i, block });
		
		if(i.registerBody) invokeRegistryMethod("registerBodyProvider",
				new Class<?>[] { IWailaDataProvider.class, Class.class },
				new Object[] { i, block });
		
		if(i.registerTail) invokeRegistryMethod("registerTailProvider",
				new Class<?>[] { IWailaDataProvider.class, Class.class },
				new Object[] { i, block });
	}
	
	public static class RegisterConfigEvent extends Event
	{
		public final FastMap<String, FastList<String>> config;
		
		public RegisterConfigEvent()
		{ config = configMap; }
		
		public void post()
		{ MinecraftForge.EVENT_BUS.post(this); }
	}
}