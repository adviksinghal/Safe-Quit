package com.sq.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftDisconnectMixin {

    @Inject(method = "disconnectFromWorld", at = @At("HEAD"), cancellable = true)
    private void safeQuit$onDisconnectFromWorld(Component message, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        IntegratedServer server = client.getSingleplayerServer();

        // Check if we are in singleplayer and the server is actively saving data
        if (server != null && server.isCurrentlySaving()) {
            // Cancel the immediate quit/disconnect sequence
            ci.cancel();

            // Quietly wait in a background worker thread until saving finishes
            new Thread(() -> {
                try {
                    while (server.isCurrentlySaving()) {
                        Thread.sleep(50); // poll every 50ms without freezing the UI thread
                    }

                    // Once saving is fully complete, execute the disconnect safely on the main client thread
                    client.execute(() -> {
                        client.disconnectFromWorld(message);
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "SafeQuit-BackgroundWorker").start();
        }
        // If the world is already saved, the mod does nothing and vanilla handles it instantly!
    }
}