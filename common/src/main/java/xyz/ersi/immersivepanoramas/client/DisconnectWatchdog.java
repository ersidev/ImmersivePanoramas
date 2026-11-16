package xyz.ersi.immersivepanoramas.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public final class DisconnectWatchdog implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("ImmersivePanoramas");
    private static final Duration WARNING_DELAY = Duration.ofSeconds(10);

    private final Thread watcher;
    private volatile String phase = "capturing panorama";

    private DisconnectWatchdog(Thread clientThread, Thread serverThread) {
        watcher = Thread.ofVirtual().name("Immersive Panoramas disconnect watchdog").start(() -> {
            try {
                Thread.sleep(WARNING_DELAY);
            } catch (InterruptedException e) {
                return;
            }
            String currentPhase = phase;
            if (currentPhase != null) {
                LOGGER.warn("Singleplayer disconnect is still running after {} seconds ({}).\n{}\n{}",
                    WARNING_DELAY.toSeconds(), currentPhase, describe(clientThread), describe(serverThread));
            }
        });
    }

    public static DisconnectWatchdog start(Minecraft client) {
        IntegratedServer server = client.getSingleplayerServer();
        if (client.level == null || server == null) {
            return null;
        }
        return new DisconnectWatchdog(Thread.currentThread(), server.getRunningThread());
    }

    public void finishingDisconnect() {
        phase = "disconnecting after panorama capture";
    }

    private static String describe(Thread thread) {
        StringBuilder stack = new StringBuilder(thread.getName()).append(" (").append(thread.getState()).append(')');
        for (StackTraceElement frame : thread.getStackTrace()) {
            stack.append("\n\tat ").append(frame);
        }
        return stack.toString();
    }

    @Override
    public void close() {
        phase = null;
        watcher.interrupt();
    }
}
