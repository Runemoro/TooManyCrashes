package org.dimdev.toomanycrashes;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.gui.screen.ConfirmChatLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.LiteralText;
import net.minecraft.util.crash.CrashReport;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dimdev.utils.HasteUpload;

@Environment(EnvType.CLIENT)
public abstract class ProblemScreen extends Screen {

  private static final Logger LOGGER = LogManager.getLogger();

  protected final CrashReport report;
  private String hasteLink = null;
  private String modListString = null;

  protected ProblemScreen(CrashReport report) {
    super(new LiteralText(""));
    this.report = report;
  }


  @Override
  public void init() {
    addButton(new ButtonWidget(1, width / 2 - 155 + 160, height / 4 + 120 + 12, 150, I18n.translate("toomanycrashes.gui.getLink"), buttonWidget -> {
      try {
        if (hasteLink == null) {
          hasteLink = HasteUpload.uploadToHaste(ModConfig.instance().hasteURL, "mccrash", report.asString());
        }
        Field uriField;
        //noinspection JavaReflectionMemberAccess
        uriField = Screen.class.getDeclaredField("clickedLink");
        uriField.setAccessible(true);
        uriField.set(ProblemScreen.this, new URI(hasteLink));
        minecraft.openScreen(new ConfirmChatLinkScreen(b -> {
        }, hasteLink, false));
      } catch (Throwable e) {
        LOGGER.error("Exception when crash menu button clicked:", e);
        buttonWidget.setMessage(I18n.translate("toomanycrashes.gui.failed"));
        buttonWidget.active = false;
      }
    }));
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
}
