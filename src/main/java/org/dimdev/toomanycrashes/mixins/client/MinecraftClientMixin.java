package org.dimdev.toomanycrashes.mixins.client;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.SaveLevelScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.Window;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import org.apache.logging.log4j.Logger;
import org.dimdev.toomanycrashes.*;
import org.dimdev.utils.GlUtil;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.io.File;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin extends ReentrantThreadExecutor<Runnable> implements PatchedClient {
    // @formatter:off
    @Shadow public static byte[] memoryReservedForCrash;
    @Shadow @Final public static Identifier DEFAULT_TEXT_RENDERER_ID;
    @Shadow @Final public static boolean IS_SYSTEM_MAC;
    @Shadow @Final private static Logger LOGGER;
    @Shadow public GameOptions options;
    @Shadow public InGameHud inGameHud;
    @Shadow public Screen currentScreen;
    @Shadow public TextureManager textureManager;
    @Shadow public TextRenderer textRenderer;
    @Shadow public Window window;
    @Shadow public Mouse mouse;
    @Shadow @Final public File runDirectory;
    @Shadow public Keyboard keyboard;
    @Shadow volatile boolean running;
    @Shadow private CrashReport crashReport;
    @Shadow private Framebuffer framebuffer;
    @Shadow private SoundManager soundManager;
    @Shadow @Final private Queue<Runnable> renderTaskQueue;
    private int clientCrashCount = 0;
    private int serverCrashCount = 0;
    public MinecraftClientMixin(String string_1) { super(string_1); }
    @Shadow public void openScreen(Screen gui) {}
    @Shadow public CrashReport addDetailsToCrashReport(CrashReport report) { return null; }
    @Shadow @Override public void close() {}
    @Shadow public abstract ClientPlayNetworkHandler getNetworkHandler();
    @Shadow protected abstract void render(boolean tick);
    @Shadow public abstract CompletableFuture<Void> reloadResources();
    @Shadow public abstract boolean forcesUnicodeFont();
    @Shadow public abstract void stop();
    @Shadow public abstract void disconnect(Screen screen);
    // @formatter:on

    /**
     * Allows the player to choose to return to the title screen after a crash, or get
     * a pasteable link to the crash report on paste.dimdev.org.
     */
    @Overwrite
    public void run() {
        while (running) {
            if (crashReport != null) {
                serverCrashCount++;
                addCrashCountToReport(crashReport);
                resetGameState();
                displayCrashScreen(crashReport);
                crashReport = null;
            }

            try {
                render(true);
            } catch (CrashException e) {
                clientCrashCount++;
                addDetailsToCrashReport(e.getReport());
                addCrashCountToReport(e.getReport());
                resetGameState();
                LOGGER.fatal("Reported exception thrown!", e);
                displayCrashScreen(e.getReport());
            } catch (Throwable e) {
                clientCrashCount++;
                CrashReport report = new CrashReport("Unexpected error", e);

                addDetailsToCrashReport(report);
                addCrashCountToReport(report);
                resetGameState();
                LOGGER.fatal("Unreported exception thrown!", e);
                displayCrashScreen(report);
            }
        }
    }

    public void addCrashCountToReport(CrashReport report) {
        report.getSystemDetailsSection().add("Client Crashes Since Restart", () -> String.valueOf(clientCrashCount));
        report.getSystemDetailsSection().add("Integrated Server Crashes Since Restart", () -> String.valueOf(serverCrashCount));
    }

    @Override
    public void displayInitErrorScreen(CrashReport report) {
        CrashUtils.outputReport(report);

        try {
            GlUtil.resetState();
            running = true;
            runGuiLoop(new InitErrorScreen(report));
        } catch (Throwable t) {
            LOGGER.error("An uncaught exception occured while displaying the init error screen, making normal report instead", t);
            printCrashReport(report);
            System.exit(report.getFile() != null ? -1 : -2);
        }
    }

    private void runGuiLoop(Screen screen) {
        openScreen(screen);

        while (running && currentScreen != null && !(currentScreen instanceof TitleScreen)) {
            window.setPhase("TooManyCrashes GUI Loop");

            if (GLX._shouldClose(window)) {
                stop();
            }

            textureManager.tick();
            currentScreen.getClass().getCanonicalName();
            soundManager.tick(true);

            mouse.updateMouse();

            RenderSystem.pushMatrix();

            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, IS_SYSTEM_MAC);
            framebuffer.beginWrite(true);
            RenderSystem.enableTexture();

            RenderSystem.clear(0xFF, IS_SYSTEM_MAC);
            RenderSystem.matrixMode(GL11.GL_PROJECTION);
            RenderSystem.loadIdentity();

            RenderSystem.ortho(
                    0,
                    window.getFramebufferWidth() / window.getScaleFactor(),
                    window.getFramebufferHeight() / window.getScaleFactor(),
                    0,
                    1000,
                    3000
            );

            RenderSystem.matrixMode(GL11.GL_MODELVIEW);
            RenderSystem.loadIdentity();
            RenderSystem.translatef(0, 0, -2000);
            RenderSystem.clear(0xFF, IS_SYSTEM_MAC);

            currentScreen.render(
                    (int) (mouse.getX() * window.getScaledWidth() / window.getWidth()),
                    (int) (mouse.getY() * window.getScaledHeight() / window.getHeight()),
                    0
            );

            framebuffer.endWrite();
            RenderSystem.popMatrix();

            RenderSystem.pushMatrix();
            framebuffer.draw(window.getWidth(), window.getHeight());
            RenderSystem.popMatrix();

            window.setFullscreen();
            RenderSystem.limitDisplayFPS(60);
            Thread.yield();
        }
    }

    public void displayCrashScreen(CrashReport report) {
        try {
            CrashUtils.outputReport(report);

            // Vanilla does this when switching to main menu but not our custom crash screen
            // nor the out of memory screen (see https://bugs.mojang.com/browse/MC-128953)
            options.debugEnabled = false;
            inGameHud.getChatHud().clear(true);

            // Display the crash screen
            runGuiLoop(new CrashScreen(report));
        } catch (Throwable t) {
            LOGGER.error("An uncaught exception occured while displaying the crash screen, making normal report instead", t);
            printCrashReport(report);
            System.exit(report.getFile() != null ? -1 : -2);
        }
    }

    @Overwrite
    public static void printCrashReport(CrashReport report) {
        CrashUtils.outputReport(report);
    }

    public void resetGameState() {
        try {
            // Free up memory such that this works properly in case of an OutOfMemoryError
            int originalReservedMemorySize = -1;
            try { // In case another mod actually deletes the memoryReserve field
                if (memoryReservedForCrash != null) {
                    originalReservedMemorySize = memoryReservedForCrash.length;
                    memoryReservedForCrash = new byte[0];
                }
            } catch (Throwable ignored) {
            }

            // Reset registered resettables
            StateManager.resetStates();

            // Close the world
            if (getNetworkHandler() != null) {
                // Fix: Close the connection to avoid receiving packets from old server
                // when playing in another world (MC-128953)
                getNetworkHandler().getConnection().disconnect(new LiteralText("[TooManyCrashes] Client crashed"));
            }

            disconnect(new SaveLevelScreen(new TranslatableText("menu.savingLevel")));
            renderTaskQueue.clear(); // Fix: method_1550(null, ...) only clears when integrated server is running

            // Reset graphics
            GlUtil.resetState();

            // Re-create memory reserve so that future crashes work well too
            if (originalReservedMemorySize != -1) {
                try {
                    memoryReservedForCrash = new byte[originalReservedMemorySize];
                } catch (Throwable ignored) {
                }
            }

            System.gc();
        } catch (Throwable t) {
            LOGGER.error("Failed to reset state after a crash", t);
            try {
                StateManager.resetStates();
                GlUtil.resetState();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Disconnect from the current world and free memory, using a memory reserve
     * to make sure that an OutOfMemory doesn't happen while doing this.
     * <p>
     * Bugs Fixed:
     * - https://bugs.mojang.com/browse/MC-128953
     * - Memory reserve not recreated after out-of memory
     */
    @Overwrite
    public void cleanUpAfterCrash() {
        resetGameState();
    }
}
