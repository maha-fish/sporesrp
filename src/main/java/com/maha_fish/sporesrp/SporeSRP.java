package com.maha_fish.sporesrp;

import com.maha_fish.sporesrp.adaptation.*;
import com.maha_fish.sporesrp.battle.PurificationBattle;
import com.maha_fish.sporesrp.block.ModBlocks;
import com.maha_fish.sporesrp.client.HUDOverlay;
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
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SporeSRP.MOD_ID)
public class SporeSRP {
    public static final String MOD_ID = "sporesrp";
    public static final Logger LOGGER = LogManager.getLogger();
    private static boolean configModified = false;

    public SporeSRP(IEventBus modEventBus, ModContainer container) {
        // 使用 ModContainer 注册配置
        container.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        // 注册配置
        modEventBus.addListener(this::registerConfigs);
        modEventBus.addListener(this::setupNetwork);
        // 注册附魔、声音、方块、物品
        ModEnchantments.register();
        ModSounds.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        Adaptations.ATTACHMENT_TYPES.register(modEventBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::registerGuiLayers);
        }

        // ---- 注册事件处理器（NeoForge事件总线） ----
        NeoForge.EVENT_BUS.register(new EvolutionEvents());
        NeoForge.EVENT_BUS.register(PhaseEffects.class);
        NeoForge.EVENT_BUS.register(new InfectionConversionHandler());
        NeoForge.EVENT_BUS.register(new PlayerDeathHandler());
        NeoForge.EVENT_BUS.register(new AdaptationEvents());
        NeoForge.EVENT_BUS.register(new SpawnEventListener());
        NeoForge.EVENT_BUS.register(new SupportSystemHandler());
        NeoForge.EVENT_BUS.register(new MoundHandler());
        NeoForge.EVENT_BUS.register(new TrackingSystemHandler());
        NeoForge.EVENT_BUS.register(new InfectedBiomeHandler());
        NeoForge.EVENT_BUS.register(new EvolutionPeriodicHandler());
        NeoForge.EVENT_BUS.register(new MoundLevelSystem());
        NeoForge.EVENT_BUS.register(new ProtoMarkedMoundHandler());
        NeoForge.EVENT_BUS.register(new ProtoSkillsHandler());
        // ---- 阶段门控（演化阶段越高，世界惩罚越重） ----
        NeoForge.EVENT_BUS.register(new PhaseGateHandler());
        // ---- 净化世界：一次性清扫 + 刷怪入口拦截 ----
        NeoForge.EVENT_BUS.register(PurifiedWorldHandler.class);
        NeoForge.EVENT_BUS.register(new EnchantmentHandler());
        NeoForge.EVENT_BUS.register(new ProtoDeathResurrectionHandler());
        NeoForge.EVENT_BUS.register(new GastgaberBuilderHandler());
        NeoForge.EVENT_BUS.register(new FullHivemindHandler());
        NeoForge.EVENT_BUS.register(new FullHivemindSkillsHandler());
        NeoForge.EVENT_BUS.register(new CDUHandler());
        NeoForge.EVENT_BUS.register(new CDUCommandHandler());
        NeoForge.EVENT_BUS.register(new FullHivemindWatcherHandler());
        NeoForge.EVENT_BUS.register(new WorldPurificationCoreHandler());
        NeoForge.EVENT_BUS.register(PurificationBattle.getInstance());
        NeoForge.EVENT_BUS.register(new ItemPickupHandler());
        NeoForge.EVENT_BUS.register(new PlayerSurvivalHandler());
        NeoForge.EVENT_BUS.register(SporeEntityCache.class);
        // Spore 配置热更新（运行时改内存 spec，免重启）
        NeoForge.EVENT_BUS.register(SporeConfigBridge.class);
        NeoForge.EVENT_BUS.register(new FullHivemindAssaultHandler());


        // ---- 命令与能力挂载 ----
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        // 服务器停止时释放跨世界静态缓存，避免换存档后残留上一世界的引用
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> WorldEvolutionData.resetCache());

        // ---- 修改Spore配置文件 ----
        // 已改为在 ServerStartedEvent 里热更新 Spore 的内存配置（SporeConfigBridge），
        // 不再于构造期改写 Spore 的 TOML 文件（旧做法需要重启才生效，且有截断/漂移/阻塞风险）。
    }

    private void registerConfigs(FMLCommonSetupEvent event) {
        // 配置已在构造时注册，此方法仅用于占位
    }

    // ========== 网络系统（NeoForge NetworkPayload） ==========
    private void setupNetwork(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MOD_ID);
        registrar.playBidirectional(
                SyncEvolutionDataPacket.TYPE,
                SyncEvolutionDataPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        SyncEvolutionDataPacket::handleClient,
                        SyncEvolutionDataPacket::handleServer
                )
        );
        registrar.playBidirectional(
                ToggleHUDPacket.TYPE,
                ToggleHUDPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ToggleHUDPacket::handleClient,
                        ToggleHUDPacket::handleServer
                )
        );
    }

    // 在 SporeSRP 构造函数或 setup 方法中添加：
    private void registerAttachments(IEventBus modEventBus) {
    }

    // ========== 事件监听 ==========
    private void registerCommands(RegisterCommandsEvent event) {
        SporeCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            WorldEvolutionData data = WorldEvolutionData.get(level);
            data.syncToPlayer(player);
        }
    }

    private void onLevelLoad(LevelEvent.Load event) {
        if (!configModified && event.getLevel() instanceof ServerLevel serverLevel) {
            if (serverLevel.dimension() == Level.OVERWORLD) {
                configModified = true;
            }
        }
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    @OnlyIn(Dist.CLIENT)
    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.parse(SporeSRP.MOD_ID + ":hud"),
                new HUDOverlay()
        );
    }
}
