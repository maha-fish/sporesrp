package com.maha_fish.sporesrp;

import com.maha_fish.sporesrp.adaptation.*;
import com.maha_fish.sporesrp.battle.PurificationBattle;
import com.maha_fish.sporesrp.block.ModBlocks;
import com.maha_fish.sporesrp.command.SporeCommands;
import com.maha_fish.sporesrp.config.SporeConfigBridge;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.enchantment.ModEnchantments;
import com.maha_fish.sporesrp.handler.*;
import com.maha_fish.sporesrp.item.ModCreativeTabs;
import com.maha_fish.sporesrp.item.ModItems;
import com.maha_fish.sporesrp.network.SyncEvolutionDataPacket;
import com.maha_fish.sporesrp.network.ToggleHUDPacket;
import com.maha_fish.sporesrp.phase.PhaseEffects;
import com.maha_fish.sporesrp.sound.ModSounds;
import com.maha_fish.sporesrp.util.SporeEntityCache;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SporeSRP.MOD_ID)
public class SporeSRP {
    public static final String MOD_ID = "sporesrp";
    public static final Logger LOGGER = LogManager.getLogger();
    private static boolean configModified = false;

    // ========== 网络通道 ==========
    public static final String NETWORK_PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MOD_ID, "main"),
            () -> NETWORK_PROTOCOL,
            NETWORK_PROTOCOL::equals,
            NETWORK_PROTOCOL::equals
    );

    // ========== 构造方法 ==========
    public SporeSRP() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);

        // ---- 注册附魔 ----
        ModEnchantments.register(modEventBus);

        // ---- 注册声音 ----
        ModSounds.register(modEventBus);

        // ---- 注册网络包 ----
        setupNetwork();

        // 注册方块和物品
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);






        // ---- 注册事件处理器 ----
        MinecraftForge.EVENT_BUS.register(new EvolutionEvents());
        MinecraftForge.EVENT_BUS.register(PhaseEffects.class);
        MinecraftForge.EVENT_BUS.register(new InfectionConversionHandler());
        MinecraftForge.EVENT_BUS.register(new PlayerDeathHandler());
        MinecraftForge.EVENT_BUS.register(new AdaptationEvents());
        MinecraftForge.EVENT_BUS.register(new SpawnEventListener());
        MinecraftForge.EVENT_BUS.register(new SupportSystemHandler());
        MinecraftForge.EVENT_BUS.register(new MoundHandler());
        MinecraftForge.EVENT_BUS.register(new TrackingSystemHandler());
        MinecraftForge.EVENT_BUS.register(new InfectedBiomeHandler());
        MinecraftForge.EVENT_BUS.register(new EvolutionPeriodicHandler());
        MinecraftForge.EVENT_BUS.register(new MoundLevelSystem());
        MinecraftForge.EVENT_BUS.register(new ProtoMarkedMoundHandler());
        MinecraftForge.EVENT_BUS.register(new ProtoSkillsHandler());
        // ---- 阶段门控（演化阶段越高，世界惩罚越重） ----
        MinecraftForge.EVENT_BUS.register(new PhaseGateHandler());
        // ---- 净化世界：一次性清扫 + 刷怪入口拦截 ----
        MinecraftForge.EVENT_BUS.register(PurifiedWorldHandler.class);


        // ---- 注册附魔事件处理器 ----
        MinecraftForge.EVENT_BUS.register(new EnchantmentHandler());

        // ---- 能力与命令 ----
        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, this::attachAdaptationCapability);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onLevelLoad);
        // 服务器停止时释放跨世界静态缓存，避免换存档后残留上一世界的引用
        MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.server.ServerStoppedEvent event) -> WorldEvolutionData.resetCache());

        // ---- 注册 Proto 致命反击技能 ----
        MinecraftForge.EVENT_BUS.register(new ProtoDeathResurrectionHandler());

        // ---- 注册 Gastgaber Builder 变种系统 ----
        MinecraftForge.EVENT_BUS.register(new GastgaberBuilderHandler());

        // ---- 注册完整心智系统 ----
        MinecraftForge.EVENT_BUS.register(new FullHivemindHandler());
        MinecraftForge.EVENT_BUS.register(new FullHivemindSkillsHandler());
        MinecraftForge.EVENT_BUS.register(new FullHivemindAssaultHandler());

        // ---- 注册 CDU 事件处理器 ----
        MinecraftForge.EVENT_BUS.register(new CDUHandler());

        // ---- 注册 CDU 命令处理器（处理 fillbiome 队列） ----
        MinecraftForge.EVENT_BUS.register(new CDUCommandHandler());

        MinecraftForge.EVENT_BUS.register(new FullHivemindWatcherHandler());

        MinecraftForge.EVENT_BUS.register(new WorldPurificationCoreHandler());

        MinecraftForge.EVENT_BUS.register(PurificationBattle.getInstance());

        // 在 SporeSRP 构造方法中添加
        MinecraftForge.EVENT_BUS.register(new ItemPickupHandler());
        MinecraftForge.EVENT_BUS.register(new PlayerSurvivalHandler());




        // ---- 注册 SporeEntityCache（静态事件监听） ----
        MinecraftForge.EVENT_BUS.register(SporeEntityCache.class);

        // ---- Spore 配置热更新（运行时改内存 spec，免重启） ----
        MinecraftForge.EVENT_BUS.register(SporeConfigBridge.class);




        // ---- 修改 Spore 配置文件 ----
        // 已改为在 ServerStartedEvent 里热更新 Spore 的内存配置（SporeConfigBridge），
        // 不再于构造期改写 Spore 的 TOML 文件（旧做法需要重启才生效，且有截断/漂移/阻塞风险）。

        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);


    }

    // ========== 网络包注册 ==========
    private void setupNetwork() {
        int id = 0;
        CHANNEL.registerMessage(id++, SyncEvolutionDataPacket.class,
                SyncEvolutionDataPacket::encode,
                SyncEvolutionDataPacket::new,
                SyncEvolutionDataPacket::handle);
        CHANNEL.registerMessage(id++, ToggleHUDPacket.class,
                ToggleHUDPacket::encode,
                ToggleHUDPacket::new,
                ToggleHUDPacket::handle);
    }

    // ========== 事件监听 ==========
    private void registerCommands(RegisterCommandsEvent event) {
        SporeCommands.register(event.getDispatcher());
    }

    private void attachAdaptationCapability(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof LivingEntity living && SporeEntityHelper.isSporeEntity(living)) {
            IAdaptation adaptation = new Adaptation();
            event.addCapability(new ResourceLocation(MOD_ID, "adaptation"),
                    AdaptationCapability.createProvider(adaptation));
        }
    }

    private void onLevelLoad(LevelEvent.Load event) {
        if (!configModified && event.getLevel() instanceof ServerLevel serverLevel) {
            if (serverLevel.dimension() == Level.OVERWORLD) {
                configModified = true;
            }
        }
    }
    @SubscribeEvent
    public void onPlayerLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            WorldEvolutionData data = WorldEvolutionData.get(level);
            data.syncToPlayer(player);
        }
    }

}