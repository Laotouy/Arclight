package io.izzel.arclight.common.mod.util.remapper.resource;

import com.google.common.io.ByteStreams;
import io.izzel.arclight.api.Unsafe;
import io.izzel.arclight.common.mod.ArclightCommon;
import io.izzel.arclight.common.mod.util.remapper.ArclightRemapper;
import io.izzel.arclight.common.mod.util.remapper.GlobalClassRepo;
import org.objectweb.asm.ClassReader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Hashtable;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;

public class RemapSourceHandler extends URLStreamHandler {

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        return new RemapSourceConnection(new URL(u.getFile()));
    }

    private static class RemapSourceConnection extends URLConnection {

        private byte[] array;

        protected RemapSourceConnection(URL url) {
            super(url);
        }

        @Override
        public void connect() throws IOException {
            try {
                byte[] bytes = ByteStreams.toByteArray(url.openStream());
                String className = new ClassReader(bytes).getClassName();
                if (className.startsWith("net/minecraft/") || className.equals("com/mojang/brigadier/tree/CommandNode")) {
                    bytes = ArclightCommon.api().platformRemapClass(bytes);
                }
                this.array = ArclightRemapper.getResourceMapper().remapClassFile(bytes, GlobalClassRepo.INSTANCE);
            } catch (java.util.zip.ZipException | java.io.FileNotFoundException e) {
                // JAR 文件可能已经被关闭或替换（插件重载时）
                // 尝试直接从文件系统重新读取 JAR 文件
                try {
                    String urlPath = url.toString();
                    if (urlPath.startsWith("jar:file:") && urlPath.contains("!")) {
                        // 解析 JAR 文件路径和内部资源路径
                        int exclamationIndex = urlPath.indexOf("!");
                        String jarPath = urlPath.substring(9, exclamationIndex); // 去掉 "jar:file:" 前缀
                        String resourcePath = urlPath.substring(exclamationIndex + 2); // 去掉 "!/" 前缀
                        
                        // 将 URL 编码的路径转换回文件路径
                        jarPath = java.net.URLDecoder.decode(jarPath, "UTF-8");
                        File jarFile = new File(jarPath);
                        
                        if (jarFile.exists()) {
                            // 直接从文件系统打开 JAR 文件（即使旧的 ClassLoader 已关闭）
                            try (JarFile jar = new JarFile(jarFile)) {
                                JarEntry entry = jar.getJarEntry(resourcePath);
                                if (entry != null) {
                                    try (InputStream is = jar.getInputStream(entry)) {
                                        byte[] bytes = ByteStreams.toByteArray(is);
                                        String className = new ClassReader(bytes).getClassName();
                                        if (className.startsWith("net/minecraft/") || className.equals("com/mojang/brigadier/tree/CommandNode")) {
                                            bytes = ArclightCommon.api().platformRemapClass(bytes);
                                        }
                                        this.array = ArclightRemapper.getResourceMapper().remapClassFile(bytes, GlobalClassRepo.INSTANCE);
                                        return;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception directReadException) {
                    // 直接读取失败，记录错误
                    directReadException.printStackTrace();
                }
                
                // 如果直接读取失败，尝试从当前线程的类加载器重新加载资源
                try {
                    String resourcePath = url.getPath();
                    if (resourcePath.contains("!")) {
                        // 提取类路径，如: /path/to/plugin.jar!/com/example/MyClass.class -> com/example/MyClass.class
                        resourcePath = resourcePath.substring(resourcePath.indexOf("!") + 2);
                    }
                    
                    // 从当前线程的类加载器（应该是新插件的类加载器）获取资源
                    ClassLoader currentLoader = Thread.currentThread().getContextClassLoader();
                    if (currentLoader != null) {
                        InputStream stream = currentLoader.getResourceAsStream(resourcePath);
                        if (stream != null) {
                            byte[] bytes = ByteStreams.toByteArray(stream);
                            String className = new ClassReader(bytes).getClassName();
                            if (className.startsWith("net/minecraft/") || className.equals("com/mojang/brigadier/tree/CommandNode")) {
                                bytes = ArclightCommon.api().platformRemapClass(bytes);
                            }
                            this.array = ArclightRemapper.getResourceMapper().remapClassFile(bytes, GlobalClassRepo.INSTANCE);
                            return;
                        }
                    }
                } catch (Exception fallbackException) {
                    // 回退失败，继续原始错误处理
                }
                
                // 如果无法从新类加载器获取，抛出原始异常
                throw new FileNotFoundException("Resource not available (possibly due to plugin reload): " + url.getFile());
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("no such file")) {
                    // JAR 文件已经被删除或替换
                    throw new FileNotFoundException("Resource not available: " + url.getFile());
                } else {
                    throw e;
                }
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            try {
                connect();
            } catch (FileNotFoundException e) {
                // 如果 connect 失败（可能因为插件重载），尝试直接返回空流或抛出异常
                // 这样 fastjson 可以优雅地处理错误而不是崩溃
                this.array = null;
            }
            
            if (this.array == null) {
                // 返回一个空的类文件而不是抛出异常，让 fastjson 能继续工作
                // 或者抛出 FileNotFoundException
                throw new FileNotFoundException(this.url.getFile());
            } else {
                return new ByteArrayInputStream(this.array);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void register() {
        try {
            Unsafe.ensureClassInitialized(URL.class);
            MethodHandle getter = Unsafe.lookup().findStaticGetter(URL.class, "handlers", Hashtable.class);
            Hashtable<String, URLStreamHandler> handlers = (Hashtable<String, URLStreamHandler>) getter.invokeExact();
            handlers.put("remap", new RemapSourceHandler());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
