package org.itxtech.mcl.impl;

import org.itxtech.mcl.Loader;
import org.itxtech.mcl.component.Downloader;

import java.io.File;
import java.io.FileOutputStream;
import java.net.Proxy;
import java.net.URL;
import java.nio.channels.Channels;

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
public class DefaultDownloader implements Downloader {
    private final Loader loader;

    public DefaultDownloader(Loader loader) {
        this.loader = loader;
    }

    @Override
    public void download(String url, File file) {
        try (var fos = new FileOutputStream(file)) {
            var proxy = loader.getProxy();
            var connection = proxy == null ? new URL(url).openConnection() : new URL(url).openConnection(new Proxy(Proxy.Type.HTTP, proxy));
            var in = connection.getInputStream();

            var buf = new byte[4096];
            int b;
            while ((b = in.read(buf)) > 0) {
                fos.write(buf, 0, b);
            }

            in.close();
        } catch (Throwable e) {
            loader.logger.logException(e);
        }
    }
}
