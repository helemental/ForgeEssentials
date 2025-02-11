package com.forgeessentials.core;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.permission.PermissionLevel;
import net.minecraftforge.permission.PermissionManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Logger;

import com.forgeessentials.api.APIRegistry;
import com.forgeessentials.api.UserIdent;
import com.forgeessentials.commons.BuildInfo;
import com.forgeessentials.commons.network.NetworkUtils;
import com.forgeessentials.commons.network.NetworkUtils.NullMessageHandler;
import com.forgeessentials.commons.network.Packet0Handshake;
import com.forgeessentials.commons.network.Packet1SelectionUpdate;
import com.forgeessentials.commons.network.Packet2Reach;
import com.forgeessentials.commons.network.Packet3PlayerPermissions;
import com.forgeessentials.commons.network.Packet5Noclip;
import com.forgeessentials.commons.network.Packet7Remote;
import com.forgeessentials.compat.CompatReiMinimap;
import com.forgeessentials.compat.HelpFixer;
import com.forgeessentials.core.commands.CommandFEInfo;
import com.forgeessentials.core.commands.CommandFeSettings;
import com.forgeessentials.core.commands.CommandUuid;
import com.forgeessentials.core.environment.Environment;
import com.forgeessentials.core.mcstats.ConstantPlotter;
import com.forgeessentials.core.mcstats.Metrics;
import com.forgeessentials.core.mcstats.Metrics.Graph;
import com.forgeessentials.core.misc.BlockModListFile;
import com.forgeessentials.core.misc.FECommandManager;
import com.forgeessentials.core.misc.TaskRegistry;
import com.forgeessentials.core.misc.TeleportHelper;
import com.forgeessentials.core.misc.Translator;
import com.forgeessentials.core.moduleLauncher.ModuleLauncher;
import com.forgeessentials.core.moduleLauncher.config.ConfigLoader.ConfigLoaderBase;
import com.forgeessentials.core.moduleLauncher.config.ConfigManager;
import com.forgeessentials.core.preloader.FELaunchHandler;
import com.forgeessentials.data.v2.DataManager;
import com.forgeessentials.util.FEChunkLoader;
import com.forgeessentials.util.PlayerInfo;
import com.forgeessentials.util.ServerUtil;
import com.forgeessentials.util.events.FEModuleEvent;
import com.forgeessentials.util.events.FEModuleEvent.FEModuleServerPreInitEvent;
import com.forgeessentials.util.events.FEModuleEvent.FEModuleServerStoppedEvent;
import com.forgeessentials.util.events.ForgeEssentialsEventFactory;
import com.forgeessentials.util.output.ChatOutputHandler;
import com.forgeessentials.util.output.LoggingHandler;
import com.forgeessentials.util.questioner.Questioner;
import com.forgeessentials.util.selections.CommandDeselect;
import com.forgeessentials.util.selections.CommandExpand;
import com.forgeessentials.util.selections.CommandExpandY;
import com.forgeessentials.util.selections.CommandPos;
import com.forgeessentials.util.selections.CommandWand;
import com.forgeessentials.util.selections.SelectionEventHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * Main mod class
 */

@Mod(modid = ForgeEssentials.MODID, name = "Forge Essentials", version = BuildInfo.BASE_VERSION, acceptableRemoteVersions = "*", dependencies = "required-after:Forge@[10.13.2.1258,);after:WorldEdit")
public class ForgeEssentials extends ConfigLoaderBase
{

    public static final String MODID = "ForgeEssentials";

    @Instance(value = MODID)
    public static ForgeEssentials instance;

    /* ------------------------------------------------------------ */

    public static final String PERM = "fe";
    public static final String PERM_CORE = PERM + ".core";
    public static final String PERM_INFO = PERM_CORE + ".info";
    public static final String PERM_VERSIONINFO = PERM_CORE + ".versioninfo";

    /* ------------------------------------------------------------ */
    /* ForgeEssentials core submodules */

    protected static ConfigManager configManager;

    protected static ModuleLauncher moduleLauncher;

    protected static TaskRegistry tasks = new TaskRegistry();

    protected static SelectionEventHandler wandHandler;

    protected static ForgeEssentialsEventFactory factory;

    protected static TeleportHelper teleportHelper;

    protected static Questioner questioner;

    protected static FECommandManager commandManager;

    protected static Metrics mcStats;

    protected static Graph mcStatsGeneralGraph;

    /* ------------------------------------------------------------ */

    protected File configDirectory;

    protected boolean debugMode = false;

    public boolean logCommandsToConsole;

    /* ------------------------------------------------------------ */

    public ForgeEssentials()
    {
        LoggingHandler.init();
        BuildInfo.getBuildInfo(FELaunchHandler.getJarLocation());
        Environment.check();
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        LoggingHandler.felog = event.getModLog();
        LoggingHandler.felog.info(String.format("Running ForgeEssentials %s (%s)", BuildInfo.getFullVersion(), BuildInfo.getBuildHash()));

        // Initialize core configuration
        Translator.load();
        initConfiguration();
        registerNetworkMessages();

        // Init McStats
        mcStats = new Metrics(MODID + "New", BuildInfo.BASE_VERSION);
        mcStatsGeneralGraph = mcStats.createGraph("general");

        // Set up logger level
        if (debugMode)
            ((Logger) LoggingHandler.felog).setLevel(Level.DEBUG);
        else
            ((Logger) LoggingHandler.felog).setLevel(Level.INFO);

        // Register core submodules
        factory = new ForgeEssentialsEventFactory();
        wandHandler = new SelectionEventHandler();
        teleportHelper = new TeleportHelper();
        questioner = new Questioner();
        APIRegistry.getFEEventBus().register(new CompatReiMinimap());

        // Load submodules
        moduleLauncher = new ModuleLauncher();
        moduleLauncher.preLoad(event);
    }

    @EventHandler
    public void load(FMLInitializationEvent e)
    {
        registerCommands();

        // Init McStats
        mcStats.createGraph("build_type").addPlotter(new ConstantPlotter(BuildInfo.getBuildType(), 1));
        mcStats.createGraph("server_type").addPlotter(new ConstantPlotter(e.getSide() == Side.SERVER ? "server" : "client", 1));
        Graph gModules = mcStats.createGraph("modules");
        for (String module : ModuleLauncher.getModuleList())
            gModules.addPlotter(new ConstantPlotter(module, 1));

        LoggingHandler.felog.info(String.format("Running ForgeEssentials %s-%s (%s)", BuildInfo.getFullVersion(), BuildInfo.getBuildType(),
                BuildInfo.getBuildHash()));
        if (BuildInfo.isOutdated())
        {
            LoggingHandler.felog.warn("-------------------------------------------------------------------------------------");
            LoggingHandler.felog.warn(String.format("WARNING! Using ForgeEssentials build #%d, latest build is #%d",//
                    BuildInfo.getBuildNumber(), BuildInfo.getBuildNumberLatest()));
            LoggingHandler.felog.warn("We highly recommend updating asap to get the latest security and bug fixes");
            LoggingHandler.felog.warn("-------------------------------------------------------------------------------------");
        }

        APIRegistry.getFEEventBus().post(new FEModuleEvent.FEModuleInitEvent(e));
    }

    @EventHandler
    public void postLoad(FMLPostInitializationEvent e)
    {
        APIRegistry.getFEEventBus().post(new FEModuleEvent.FEModulePostInitEvent(e));
        commandManager = new FECommandManager();
    }

    /* ------------------------------------------------------------ */

    private void initConfiguration()
    {
        configDirectory = new File(ServerUtil.getBaseDir(), "/ForgeEssentials");
        configManager = new ConfigManager(configDirectory, "main");
        configManager.registerLoader(configManager.getMainConfigName(), this);
        configManager.registerLoader(configManager.getMainConfigName(), new FEConfig());
        configManager.registerLoader(configManager.getMainConfigName(), new ChatOutputHandler());
    }

    private void registerNetworkMessages()
    {
        // Load network packages
        NetworkUtils.registerMessage(new IMessageHandler<Packet0Handshake, IMessage>() {
            @Override
            public IMessage onMessage(Packet0Handshake message, MessageContext ctx)
            {
                PlayerInfo.get(ctx.getServerHandler().playerEntity).setHasFEClient(true);
                return null;
            }
        }, Packet0Handshake.class, 0, Side.SERVER);
        NetworkUtils.registerMessageProxy(Packet1SelectionUpdate.class, 1, Side.CLIENT, new NullMessageHandler<Packet1SelectionUpdate>() {
            /* dummy */
        });
        NetworkUtils.registerMessageProxy(Packet2Reach.class, 2, Side.CLIENT, new NullMessageHandler<Packet2Reach>() {
            /* dummy */
        });
        NetworkUtils.registerMessageProxy(Packet3PlayerPermissions.class, 3, Side.CLIENT, new NullMessageHandler<Packet3PlayerPermissions>() {
            /* dummy */
        });
        NetworkUtils.registerMessageProxy(Packet5Noclip.class, 5, Side.CLIENT, new NullMessageHandler<Packet5Noclip>() {
            /* dummy */
        });
        NetworkUtils.registerMessageProxy(Packet7Remote.class, 7, Side.CLIENT, new NullMessageHandler<Packet7Remote>() {
            /* dummy */
        });

    }

    private void registerCommands()
    {
        FECommandManager.registerCommand(new CommandFEInfo());
        FECommandManager.registerCommand(new CommandFeSettings());
        FECommandManager.registerCommand(new CommandWand());
        FECommandManager.registerCommand(new CommandUuid());
        if (!ModuleLauncher.getModuleList().contains("WEIntegrationTools"))
        {
            FECommandManager.registerCommand(new CommandPos(1));
            FECommandManager.registerCommand(new CommandPos(2));
            FECommandManager.registerCommand(new CommandDeselect());
            FECommandManager.registerCommand(new CommandExpand());
            FECommandManager.registerCommand(new CommandExpandY());
        }
    }

    /* ------------------------------------------------------------ */

    @EventHandler
    public void serverPreInit(FMLServerAboutToStartEvent e)
    {
        // Initialize data manager once server begins to start
        DataManager.setInstance(new DataManager(new File(ServerUtil.getWorldPath(), "FEData/json")));
        APIRegistry.getFEEventBus().post(new FEModuleServerPreInitEvent(e));
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent e)
    {
        mcStats.start();
        BlockModListFile.makeModList();
        BlockModListFile.dumpFMLRegistries();
        ForgeChunkManager.setForcedChunkLoadingCallback(this, new FEChunkLoader());

        ServerUtil.replaceCommand("help", new HelpFixer()); // Will be overwritten again by commands module

        registerPermissions();

        APIRegistry.getFEEventBus().post(new FEModuleEvent.FEModuleServerInitEvent(e));
    }

    @EventHandler
    public void serverStarted(FMLServerStartedEvent e)
    {
        APIRegistry.getFEEventBus().post(new FEModuleEvent.FEModuleServerPostInitEvent(e));

        // TODO: what the fuck? I don't think we should just go and delete all commands colliding with ours!
        // CommandSetChecker.remove();
        FECommandManager.registerCommands();

        // Do permission registration in first server tick.
        // TODO This can be removed if the Permission API gets accepted!
        FMLCommonHandler.instance().bus().register(new CommandPermissionRegistrationHandler());
    }

    public static final class CommandPermissionRegistrationHandler
    {
        @SubscribeEvent
        public void serverTickEvent(ServerTickEvent event)
        {
            PermissionManager.registerCommandPermissions();
            FMLCommonHandler.instance().bus().unregister(this);
        }
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent e)
    {
        APIRegistry.getFEEventBus().post(new FEModuleEvent.FEModuleServerStopEvent(e));
        PlayerInfo.discardAll();
    }

    @EventHandler
    public void serverStopped(FMLServerStoppedEvent e)
    {
        mcStats.stop();
        APIRegistry.getFEEventBus().post(new FEModuleServerStoppedEvent(e));
        FECommandManager.clearRegisteredCommands();
        Translator.save();
    }

    protected void registerPermissions()
    {
        APIRegistry.perms.registerPermission(PERM_VERSIONINFO, PermissionLevel.OP, "Shows notification to the player if FE version is outdated");

        APIRegistry.perms.registerPermission("mc.help", PermissionLevel.TRUE, "Help command");

        // Teleport
        APIRegistry.perms.registerPermissionProperty(TeleportHelper.TELEPORT_COOLDOWN, "5", "Allow bypassing teleport cooldown");
        APIRegistry.perms.registerPermissionProperty(TeleportHelper.TELEPORT_WARMUP, "3", "Allow bypassing teleport warmup");
        APIRegistry.perms.registerPermissionPropertyOp(TeleportHelper.TELEPORT_COOLDOWN, "0");
        APIRegistry.perms.registerPermissionPropertyOp(TeleportHelper.TELEPORT_WARMUP, "0");
        APIRegistry.perms.registerPermission(TeleportHelper.TELEPORT_FROM, PermissionLevel.TRUE, "Allow being teleported from a certain location / dimension");
        APIRegistry.perms.registerPermission(TeleportHelper.TELEPORT_TO, PermissionLevel.TRUE, "Allow being teleported to a certain location / dimension");

        CommandFeSettings.addAlias("Teleport", "warmup", TeleportHelper.TELEPORT_WARMUP);
        CommandFeSettings.addAlias("Teleport", "cooldown", TeleportHelper.TELEPORT_COOLDOWN);
    }

    /* ------------------------------------------------------------ */

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void playerLoggedInEvent(PlayerLoggedInEvent event)
    {
        if (event.player instanceof EntityPlayerMP)
        {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            UserIdent.login(player);
            PlayerInfo.login(player.getPersistentID());

            if (FEConfig.checkSpacesInNames)
            {
                Pattern pattern = Pattern.compile("\\s");
                Matcher matcher = pattern.matcher(player.getGameProfile().getName());
                if (matcher.find())
                {
                    String msg = Translator.format("Invalid name \"%s\" containing spaces. Please change your name!", player.getCommandSenderName());
                    player.playerNetServerHandler.kickPlayerFromServer(msg);
                }
            }

            // Show version notification
            if (BuildInfo.isOutdated() && UserIdent.get(player).checkPermission(PERM_VERSIONINFO))
                ChatOutputHandler.chatWarning(player,
                        String.format("ForgeEssentials build #%d outdated. Current build is #%d. Consider updating to get latest security and bug fixes.", //
                                BuildInfo.getBuildNumber(), BuildInfo.getBuildNumberLatest()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void playerLoggedOutEvent(PlayerLoggedOutEvent event)
    {
        if (event.player instanceof EntityPlayerMP)
        {
            PlayerInfo.logout(event.player.getPersistentID());
            UserIdent.logout((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void playerRespawnEvent(PlayerRespawnEvent event)
    {
        if (event.player instanceof EntityPlayerMP)
        {
            UserIdent.get((EntityPlayerMP) event.player);
        }
    }

    /* ------------------------------------------------------------ */

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void commandEvent(CommandEvent event)
    {
        if (logCommandsToConsole)
        {
            LoggingHandler.felog.info(String.format("Player \"%s\" used command \"/%s %s\"", event.sender.getCommandSenderName(),
                    event.command.getCommandName(), StringUtils.join(event.parameters, " ")));
        }
    }

    /* ------------------------------------------------------------ */

    @Override
    public void load(Configuration config, boolean isReload)
    {
        if (!config.get(FEConfig.CONFIG_CAT, "versionCheck", true, "Check for newer versions of ForgeEssentials on load?").getBoolean())
            BuildInfo.cancelVersionCheck();
        configManager.setUseCanonicalConfig(config.get(FEConfig.CONFIG_CAT, "canonicalConfigs", false,
                "For modules that support it, place their configs in this file.").getBoolean());
        debugMode = config.get(FEConfig.CONFIG_CAT, "debug", false, "Activates developer debug mode. Spams your FML logs.").getBoolean();
        HelpFixer.hideWorldEditCommands = config.get(FEConfig.CONFIG_CAT, "hide_worldedit_help", true,
                "Hide WorldEdit commands from /help and only show them in //help command").getBoolean();
        logCommandsToConsole = config.get(FEConfig.CONFIG_CAT, "logCommands", false, "Log commands to console").getBoolean();
    }

    /* ------------------------------------------------------------ */

    public static ConfigManager getConfigManager()
    {
        return configManager;
    }

    public static Metrics getMcStats()
    {
        return mcStats;
    }

    public static Graph getMcStatsGeneralGraph()
    {
        return mcStatsGeneralGraph;
    }

    public static File getFEDirectory()
    {
        return instance.configDirectory;
    }

    public static boolean isDebug()
    {
        return instance.debugMode;
    }

}
