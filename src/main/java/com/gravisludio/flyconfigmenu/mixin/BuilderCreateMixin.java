package com.gravisludio.flyconfigmenu.mixin;

import com.gravisludio.flyconfigmenu.config.ConfigRegistry;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.catnip.config.Builder;
import com.zurrtum.create.catnip.config.ConfigBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

/**
 * Create Fly's 3-argument {@code Builder.create} delegates to the 4-argument one, so a single hook at its RETURN sees
 * every config built through the shared config system (Create's own, and every addon's).
 */
@Mixin(Builder.class)
public class BuilderCreateMixin {
    @Inject(
        method = "create(Ljava/util/function/Supplier;Ljava/lang/String;Ljava/lang/String;Z)Lcom/zurrtum/create/catnip/config/ConfigBase;",
        at = @At("RETURN")
    )
    private static void flyconfigmenu$onCreate(
        Supplier<?> factory,
        String id,
        String type,
        boolean single,
        CallbackInfoReturnable<ConfigBase> cir,
        @Local Builder builder
    ) {
        ConfigRegistry.register(new ConfigRegistry.Entry(id, type, single, cir.getReturnValue(), builder));
    }
}
