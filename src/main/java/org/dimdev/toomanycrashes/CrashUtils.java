package org.dimdev.toomanycrashes;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.crash.CrashReport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class CrashUtils {
    private static final Logger LOGGER = LogManager.getLogger("TMC");
    private static boolean clientSide;

    static {
        try {
            clientSide = MinecraftClient.getInstance() != null;
        } catch (NoClassDefFoundError e) {
            clientSide = false;
        }
    }

    public static void outputReport(CrashReport report) {
        try {
            if (report.getFile() == null) {
                String reportName = "crash-";
                reportName += LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                reportName += clientSide && MinecraftClient.getInstance().isOnThread() ? "-client" : "-server";
                reportName += ".txt";

                File reportsDir = new File(FabricLoader.getInstance().getGameDirectory(), "crash-reports");
                File reportFile = new File(reportsDir, reportName);

                report.writeToFile(reportFile);
            }
        } catch (Throwable e) {
            LOGGER.fatal("Failed saving report", e);
        }

        LOGGER.fatal(
                "Minecraft ran into a problem! " +
                (report.getFile() != null ? "Report saved to: " + report.getFile() : "Crash report could not be saved.") +
                "\n" +
                report.asString()
        );
    }
}
