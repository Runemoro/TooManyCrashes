package org.dimdev.toomanycrashes;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashReport;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dimdev.utils.HasteUpload;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Environment(EnvType.CLIENT)
public abstract class ProblemScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    protected final CrashReport report;
    private String reportLink = null;
    private String modListString = null;
    protected int fileNameLeft = Integer.MAX_VALUE;
    protected int fileNameRight = Integer.MIN_VALUE;
    protected int fileNameTop = Integer.MAX_VALUE;
    protected int fileNameBottom = Integer.MIN_VALUE;

    protected ProblemScreen(CrashReport report) {
        super(new LiteralText(""));
        this.report = report;
    }

    @Override
    public void init() {
        addButton(new ButtonWidget(width / 2 - 155 + 160, height / 4 + 120 + 12, 150, 20, I18n.translate("toomanycrashes.gui.getLink"),
                buttonWidget -> {
                    try {
                        if (reportLink == null) {
                            reportLink = HasteUpload.uploadToHaste(ModConfig.instance().hasteURL, "mccrash", report.asString());
                        }

                        Util.getOperatingSystem().open(reportLink);
                    } catch (Throwable e) {
                        LOGGER.error("Exception when crash menu button clicked:", e);
                        buttonWidget.setMessage(I18n.translate("toomanycrashes.gui.failed"));
                        buttonWidget.active = false;
                    }
                }));
    }

    @Override
    public boolean mouseClicked(double x, double y, int int_1) {
        if (x >= fileNameLeft && x <= fileNameRight && y >= fileNameTop && y <= fileNameBottom) {
            File file = report.getFile();

            if (file != null) {
                Util.getOperatingSystem().open(file);
            }
        }
        return super.mouseClicked(x, y, int_1);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    protected String getModListString() {
        if (modListString == null) {
            Set<ModMetadata> suspectedMods = ((PatchedCrashReport) report).getSuspectedMods();
            if (suspectedMods == null) {
                return modListString = I18n.translate("toomanycrashes.crashscreen.identificationErrored");
            }
            List<String> modNames = new ArrayList<>();
            for (ModMetadata mod : suspectedMods) {
                modNames.add(mod.getName());
            }
            if (modNames.isEmpty()) {
                modListString = I18n.translate("toomanycrashes.crashscreen.unknownCause");
            } else {
                modListString = StringUtils.join(modNames, ", ");
            }
        }
        return modListString;
    }

    protected void drawFileNameString(int y) {
        String fileNameString =
                report.getFile() != null ? "\u00A7n" + report.getFile().getName() : I18n.translate("toomanycrashes.crashscreen.reportSaveFailed");
        int length = font.getStringWidth(fileNameString);
        fileNameLeft = width / 2 - length / 2;
        fileNameRight = width / 2 + length / 2;
        drawString(font, fileNameString, fileNameLeft, y += 11, 0x00FF00);
        fileNameTop = y;
        fileNameBottom = y + 10;
    }
}
