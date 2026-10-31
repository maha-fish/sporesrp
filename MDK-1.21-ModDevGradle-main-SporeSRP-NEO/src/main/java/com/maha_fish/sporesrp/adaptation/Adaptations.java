package com.maha_fish.sporesrp.adaptation;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class Adaptations {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "sporesrp");

    // 修正：显式指定类型，使用 new Supplier<>() 或直接注册
    public static final Supplier<AttachmentType<IAdaptation>> ADAPTATION =
            ATTACHMENT_TYPES.register("adaptation",
                    () -> AttachmentType.builder((Supplier<IAdaptation>) Adaptation::new).build()
            );
}