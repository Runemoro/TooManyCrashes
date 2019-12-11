package org.dimdev.toomanycrashes.mixins;

import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import org.dimdev.toomanycrashes.PatchedIntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin implements PatchedIntegratedServer {
    private boolean crashScheduled = false;

    @Override
    public void scheduleCrash() {
        crashScheduled = true;
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void beforeTick(CallbackInfo ci) {
        if (crashScheduled) {
            throw new CrashException(new CrashReport("Manually triggered server-side debug crash", new Throwable()));
        }
    }
}
