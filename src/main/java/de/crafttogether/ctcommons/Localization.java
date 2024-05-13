package de.crafttogether.ctcommons;

import de.crafttogether.common.localization.LocalizationEnum;
import de.crafttogether.common.localization.LocalizationManager;

@SuppressWarnings("unused")
public class Localization extends LocalizationEnum {
    public static final Localization PREFIX = new Localization("prefix", "<gold>{pluginName} </gold><dark_gray>» </dark_gray>");

    public static final Localization CONFIG_RELOADED = new Localization("config.reloaded", "<green>Configuration reloaded.</green>");

    public static final Localization UPDATE_CHECK = new Localization("update.check", "<prefix/><green>Retrieving update information...</green>");
    public static final Localization UPDATE_LASTBUILD = new Localization("update.lastBuild", "<prefix/><green>Your installed version is up to date</green>");
    public static final Localization UPDATE_RELEASE = new Localization("update.devBuild", """
            <hover:show_text:'<green>Click here to download this version'><click:open_url:'{url}'><prefix/><green>A new full version was released!</green>
            <prefix/><green>Version: </green><yellow>{currentVersion} (build: {currentBuild})</yellow>
            <prefix/><green>FileName: </green><yellow>{fileName}</yellow>
            <prefix/><green>FileSize: </green><yellow>{fileSize}</yellow>
            <prefix/><red>You are on version: </red><yellow>{installedVersion} (build: {installedBuild})</yellow></click></hover>""");
    public static final Localization UPDATE_DEVBUILD = new Localization("update.release", """
            <hover:show_text:'<green>Click here to download this version'><click:open_url:'{url}'><prefix/><green>A new development build is available!</green>
            <prefix/><green>Version: </green><yellow>{currentVersion} (build: {currentBuild})</yellow>
            <prefix/><green>FileName: </green><yellow>{fileName}</yellow>
            <prefix/><green>FileSize: </green><yellow>{fileSize}</yellow>
            <prefix/><red>You are on version: </red><yellow>{installedVersion} (build: {installedBuild})</yellow></click></hover>""");
    public static final Localization UPDATE_ERROR = new Localization("update.error", "<prefix/><hover:show_text:'<white>{error}'><red>Unable to retrieve update informations</red></hover>");

    private Localization(String name, String defValue) {
        super(name, defValue);
    }

    @Override
    public LocalizationManager getManager() {
        return CTCommons.getLocalizationManager();
    }
}