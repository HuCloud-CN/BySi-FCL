package com.tungsten.fclcore.mod.multimc;

import static com.tungsten.fclcore.download.LibraryAnalyzer.LibraryType.FABRIC;
import static com.tungsten.fclcore.download.LibraryAnalyzer.LibraryType.FORGE;
import static com.tungsten.fclcore.download.LibraryAnalyzer.LibraryType.LITELOADER;

import com.tungsten.fclcore.download.LibraryAnalyzer;
import com.tungsten.fclcore.game.DefaultGameRepository;
import com.tungsten.fclcore.mod.ModAdviser;
import com.tungsten.fclcore.mod.ModpackExportInfo;
import com.tungsten.fclcore.task.Task;
import com.tungsten.fclcore.util.Logging;
import com.tungsten.fclcore.util.gson.JsonUtils;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Export the game to a mod pack file.
 */
// Todo : fix
public class MultiMCModpackExportTask extends Task<Void> {
    private final DefaultGameRepository repository;
    private final String versionId;
    private final List<String> whitelist;
    private final MultiMCInstanceConfiguration configuration;
    private final File output;

    /**
     * @param output    mod pack file.
     * @param versionId to locate version.json
     */
    public MultiMCModpackExportTask(DefaultGameRepository repository, String versionId, List<String> whitelist, MultiMCInstanceConfiguration configuration, File output) {
        this.repository = repository;
        this.versionId = versionId;
        this.whitelist = whitelist;
        this.configuration = configuration;
        this.output = output;

        onDone().register(event -> {
            if (event.isFailed()) output.delete();
        });
    }

    @Override
    public void execute() throws Exception {
        ArrayList<String> blackList = new ArrayList<>(ModAdviser.MODPACK_BLACK_LIST);
        blackList.add(versionId + ".jar");
        blackList.add(versionId + ".json");
        Logging.LOG.info("Compressing game files without some files in blacklist, including files or directories: usernamecache.json, asm, logs, backups, versions, assets, usercache.json, libraries, crash-reports, launcher_profiles.json, NVIDIA, TCNodeTracker");
        try (Zipper zip = new Zipper(output.toPath())) {
            zip.putDirectory(repository.getRunDirectory(versionId).toPath(), ".minecraft", path -> Modpack.acceptFile(path, blackList, whitelist));

            LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(repository.getResolvedPreservingPatchesVersion(versionId));
            String gameVersion = repository.getGameVersion(versionId)
                    .orElseThrow(() -> new IOException("Cannot parse the version of " + versionId));
            List<MultiMCManifest.MultiMCManifestComponent> components = new ArrayList<>();
            components.add(new MultiMCManifest.MultiMCManifestComponent(true, false, "net.minecraft", gameVersion));
            analyzer.getVersion(FORGE).ifPresent(forgeVersion ->
                    components.add(new MultiMCManifest.MultiMCManifestComponent(false, false, "net.minecraftforge", forgeVersion)));
            analyzer.getVersion(LITELOADER).ifPresent(liteLoaderVersion ->
                    components.add(new MultiMCManifest.MultiMCManifestComponent(false, false, "com.mumfrey.liteloader", liteLoaderVersion)));
            analyzer.getVersion(FABRIC).ifPresent(fabricVersion ->
                    components.add(new MultiMCManifest.MultiMCManifestComponent(false, false, "net.fabricmc.fabric-loader", fabricVersion)));
            MultiMCManifest mmcPack = new MultiMCManifest(1, components);
            zip.putTextFile(JsonUtils.GSON.toJson(mmcPack), "mmc-pack.json");

            StringWriter writer = new StringWriter();
            configuration.toProperties().store(writer, "Auto generated by Hello Minecraft! Launcher");
            zip.putTextFile(writer.toString(), "instance.cfg");

            zip.putTextFile("", ".packignore");
        }
    }

    public static final ModpackExportInfo.Options OPTION = new ModpackExportInfo.Options().requireMinMemory();
}
