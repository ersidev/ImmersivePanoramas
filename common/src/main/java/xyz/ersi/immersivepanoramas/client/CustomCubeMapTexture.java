package xyz.ersi.immersivepanoramas.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class CustomCubeMapTexture extends AbstractTexture {
    private static final int[] SUFFIX_MAPPING = {1, 3, 5, 4, 0, 2};

    private final Identifier id;
    private boolean loaded = false;

    public CustomCubeMapTexture(Identifier id) {
        this.id = id;
    }

    public boolean isLoaded() {
        return this.loaded;
    }

    public synchronized boolean loadFromDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }

        NativeImage[] faces = new NativeImage[6];
        try {
            for (int i = 0; i < 6; i++) {
                Path file = dir.resolve("panorama_" + i + ".png");
                if (!Files.isRegularFile(file)) {
                    closeImages(faces);
                    return false;
                }
                try (InputStream in = Files.newInputStream(file)) {
                    faces[i] = NativeImage.read(in);
                }
            }
            uploadFaces(faces);
            this.loaded = true;
            return true;
        } catch (Exception e) {
            ModLogger.error("Failed to load custom panorama from directory {}", dir, e);
            this.loaded = false;
            return false;
        } finally {
            closeImages(faces);
        }
    }

    public synchronized void uploadFaces(NativeImage[] faces) {
        int width = faces[0].getWidth();
        int height = faces[0].getHeight();

        try (NativeImage stackedImage = new NativeImage(width, height * 6, false)) {
            for (int i = 0; i < 6; i++) {
                int faceIndex = SUFFIX_MAPPING[i];
                faces[faceIndex].copyRect(stackedImage, 0, 0, 0, i * height, width, height, false, true);
            }

            GpuDevice device = RenderSystem.getDevice();
            this.releaseTextures();
            int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_CUBEMAP_COMPATIBLE;
            this.texture = device.createTexture(this.id::toString, usage, GpuFormat.RGBA8_UNORM, width, height, 6, 1);
            this.textureView = device.createTextureView(this.texture);
            CommandEncoder encoder = device.createCommandEncoder();
            GpuBufferSlice stagingBuffer = encoder.transientMemory().uploadStaging(stackedImage.getPixelBytes(), 1L, 16);

            for (int i = 0; i < 6; i++) {
                encoder.copyBufferToTexture(stagingBuffer, 0, height * i, stackedImage.getWidth(), stackedImage.getHeight(), this.texture, 0, 0, width, height, 0, i);
            }
            encoder.submit();
            this.loaded = true;
        }
    }

    private static void closeImages(NativeImage[] faces) {
        for (int i = 0; i < faces.length; i++) {
            if (faces[i] != null) {
                faces[i].close();
                faces[i] = null;
            }
        }
    }

    @Override
    public void close() {
        super.close();
        this.loaded = false;
    }
}
