package org.itxtech.mcl.module.builtin;

import org.apache.commons.cli.Option;
import org.fusesource.jansi.Ansi;
import org.itxtech.mcl.Utility;
import org.itxtech.mcl.component.Repository;
import org.itxtech.mcl.module.MclModule;
import org.itxtech.mcl.pkg.MclPackage;
import org.objectweb.asm.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/*
 *
 * Mirai Console Loader
 *
 * Copyright (C) 2020-2022 iTX Technologies
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author PeratX
 * @website https://github.com/iTXTech/mirai-console-loader
 *
 */
public class Updater extends MclModule {
    private boolean showNotice = false;

    @Override
    public String getName() {
        return "updater";
    }

    @Override
    public void prepare() {
        loader.options.addOption(Option.builder("u").desc("Update packages")
                .longOpt("update").build());
//        loader.options.addOption(Option.builder("q").desc("Remove outdated files while updating")
//                .longOpt("delete").build());
    }

    @Override
    public void load() {
        for (var pkg : loader.packageManager.getPackages()) {
            try {
                check(pkg);
            } catch (Exception e) {
                loader.logger.error("Failed to verify package \"" + pkg.id + "\"");
                loader.logger.logException(e);
            }
        }

        if (showNotice) {
            loader.logger.warning(Ansi.ansi()
                    .fgYellow()
                    .a("Run ")
                    .reset().fgBrightYellow()
                    .a("./mcl -u")
                    .reset().fgYellow()
                    .a(" to update packages.")
            );
        }

        loader.logger.info("Lib checks finished.");
    }

    public void check(MclPackage pack) throws Exception {
        var update = loader.cli.hasOption("u");
        var force = pack.isVersionLocked();
        var down = false;
        if (!pack.getJarFile().exists()) {
            if (!"".equals(pack.version)) {
                loader.logger.warning("Package \"" + pack.id + "\" doesn't exist.");
            }

            down = true;
        }

        var ver = "";
        Repository.PackageInfo info = null;
        if (pack.channel.startsWith("maven")) {
            ver = loader.repo.getLatestVersionFromMaven(pack.id, pack.channel);
        } else {
            info = loader.repo.fetchPackage(pack.id);
            if (pack.type.isEmpty()) {
                pack.type = MclPackage.getType(info.type);
            }

            if (pack.channel.isEmpty()) {
                pack.channel = MclPackage.getChannel(info.defaultChannel);
            }

            if (!info.channels.containsKey(pack.channel)) {
                loader.logger.error(Ansi.ansi()
                        .fgBrightRed()
                        .a("Invalid update channel ")
                        .fgBrightBlue().append("\"").a(pack.channel).a("\"")
                        .fgBrightRed()
                        .a(" for package ")
                        .fgBrightYellow().a("\"").a(pack.id).a("\"")
                );

                loader.saveConfig();
                return;
            }

            ver = info.getLatestVersion(pack.channel);
        }

        if ((update && !pack.version.equals(ver) && !force) || pack.version.trim().isEmpty()) {
//            if (loader.cli.hasOption("q")) {
            pack.removeFiles();
//            } else if (pack.type.equals(MclPackage.TYPE_PLUGIN)) {
//                var dir = new File(pack.type);
//                pack.getJarFile().renameTo(new File(dir, pack.getBasename() + ".jar.bak"));
//            }
            pack.version = ver;
            down = true;
        }

        if (!down && !pack.version.equals(ver)) {
            loader.logger.warning(Ansi.ansi()
                    .fgBrightRed()
                    .a("Package ")
                    .reset().fgBrightYellow().a("\"").a(pack.id).a("\"")
                    .reset().fgBrightRed().a(" has newer version ")
                    .reset().fgBrightYellow().a("\"").a(ver).a("\"")
            );

            showNotice = true;
        }

        if (down) {
            loader.logger.info(Ansi.ansi()
                    .a("Updating ")
                    .fgBrightYellow()
                    .a("\"").a(pack.id).a("\"").reset()
                    .a(" to v").fgBrightYellow().a(pack.version)
            );

            if (!Utility.checkLocalFile(pack)) {
                downloadFile(pack, info);
            }

            var result = Utility.checkLocalFile(pack);
            try {
                Files.delete(pack.getSha1File().toPath());
            } catch (IOException ignored) {
                // Do nothing
            }

            if (!result) {
                loader.logger.error(Ansi.ansi()
                        .fgBrightRed()
                        .a("The library file ")
                        .fgBrightYellow().a("\"").a(pack.id).a("\"")
                        .fgBrightRed()
                        .a(" is still corrupted, please 重开 :(")
                );

                loader.saveConfig();
                return;
            }

            if (pack.id.equals("net.mamoe:mirai-console")) {
                loader.logger.info("[ASM] Patching mirai-console...");
                try (var fs = FileSystems.newFileSystem(pack.getJarFile().toPath())) {
                    Path fsPath = fs.getPath("/net/mamoe/mirai/console/enduserreadme/EndUserReadme.class");

                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    ClassReader reader = new ClassReader(
                            Files.readAllBytes(fsPath)
                    );

                    ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                            if (mv != null) {
                                switch (name + descriptor) {
                                    case "put(Ljava/lang/String;Lkotlin/jvm/functions/Function1;)V",
                                            "putAll(Ljava/lang/String;)V",
                                            "putAll$flush(Ljava/util/List;Lnet/mamoe/mirai/console/enduserreadme/EndUserReadme;Lkotlin/jvm/internal/Ref$ObjectRef;)V"
                                            -> {
                                        loader.logger.info("[ASM] Processing method: " + name);

                                        // Clear method body
                                        Type type = Type.getType(descriptor);
                                        Type[] argTypes = type.getArgumentTypes();

                                        boolean isStatic = ((access & Opcodes.ACC_STATIC) != 0);
                                        int localSize = isStatic ? 0 : 1;
                                        for (Type argType : argTypes) {
                                            localSize += argType.getSize();
                                        }

                                        mv.visitCode();
                                        mv.visitInsn(Opcodes.RETURN);
                                        mv.visitMaxs(type.getReturnType().getSize(), localSize);
                                        mv.visitEnd();

                                        return null;
                                    }
                                }
                            }

                            return mv;
                        }
                    };

                    reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    Files.write(fsPath, writer.toByteArray());
                } catch (Exception ex) {
                    loader.logger.error("Patch failed!");
                    loader.logger.error(ex);
                }
            }
        }

        loader.saveConfig();
    }

    public void downloadFile(MclPackage pack, Repository.PackageInfo info) {
        var dir = new File(pack.type);
        dir.mkdirs();
        var name = pack.getName();
        var jar = name + "-" + pack.version + ".jar";
        var metadata = name + "-" + pack.version + ".mirai.metadata";

        var jarUrl = loader.repo.getJarUrl(pack, info);
        if (jarUrl.isEmpty()) {
            loader.logger.error(Ansi.ansi()
                    .a("Cannot download package ")
                    .fgBrightYellow().a("\"").a(pack.id).a("\"")
            );
            return;
        }

        var index = jarUrl.lastIndexOf(name);
        if (index != -1) {
            jar = jarUrl.substring(index);
        }
        down(jarUrl, new File(dir, jar));

        var sha1Url = loader.repo.getSha1Url(pack, info, jarUrl);
        var sha1 = jar + ".sha1";
        down(sha1Url, new File(dir, sha1));

        var metadataUrl = loader.repo.getMetadataUrl(pack, info);
        if (metadataUrl.isEmpty()) {
            return;
        }
        down(metadataUrl, new File(dir, metadata));
    }

    public void down(String url, File file) {
        loader.logger.info("Downloading quietly: " + file.getName());
        loader.downloader.download(url, file);
    }
}
