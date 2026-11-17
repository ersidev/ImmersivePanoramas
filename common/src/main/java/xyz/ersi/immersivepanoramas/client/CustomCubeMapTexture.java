package xyz.ersi.immersivepanoramas.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class CustomCubeMapTexture extends AbstractTexture {
    private static final int[] SUFFIX_MAPPING = {1, 3, 5, 4, 0, 2};

    private final Identifier id;

    public CustomCubeMapTexture(Identifier id) {
        this.id = id;
        this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
    }

    public boolean isLoaded() {
        return this.textureView != null;
    }

    public synchronized boolean loadFromDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }

        NativeImage[] faces = new NativeImage[SUFFIX_MAPPING.length];
        try {
            for (int i = 0; i < faces.length; i++) {
                Path file = dir.resolve(PanoramaStorage.faceFileName(i));
                if (!Files.isRegularFile(file)) {
                    return false;
                }
                try (InputStream in = Files.newInputStream(file)) {
                    faces[i] = NativeImage.read(in);
                }
            }
            uploadFaces(faces);
            return true;
        } catch (Exception e) {
            ModLogger.error("Failed to load custom panorama from directory {}", dir, e);
            return false;
        } finally {
            PanoramaStorage.closeImages(faces);
        }
    }

    private void uploadFaces(NativeImage[] faces) {
        if (faces.length != SUFFIX_MAPPING.length) {
            throw new IllegalArgumentException("A panorama must have six faces");
        }
        int width = faces[0].getWidth();
        int height = faces[0].getHeight();
        if (width != height) {
            throw new IllegalArgumentException("Panorama faces must be square");
        }

        // match the readback buffer's face order and bottom-up rows
        try (NativeImage stackedImage = new NativeImage(width, height * faces.length, false)) {
            for (int i = 0; i < faces.length; i++) {
                NativeImage face = faces[i];
                if (face.getWidth() != width || face.getHeight() != height) {
                    throw new IllegalArgumentException("Panorama faces must have matching dimensions");
                }
                face.copyRect(stackedImage, 0, 0, 0, i * height, width, height, false, true);
            }

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            GpuBufferSlice stagingBuffer = encoder.transientMemory().uploadStaging(stackedImage.getPixelBytes(), 1L, 16);
            uploadBuffer(encoder, stagingBuffer, width);
        }
    }

    synchronized void uploadReadback(GpuBufferSlice readback, int faceSize) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        uploadBuffer(encoder, readback, faceSize);
    }

    private void uploadBuffer(CommandEncoder encoder, GpuBufferSlice buffer, int faceSize) {
        GpuDevice device = RenderSystem.getDevice();
        int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_CUBEMAP_COMPATIBLE;
        GpuTexture newTexture = device.createTexture(this.id::toString, usage, GpuFormat.RGBA8_UNORM, faceSize, faceSize, SUFFIX_MAPPING.length, 1);
        GpuTextureView newView = null;
        try {
            newView = device.createTextureView(newTexture);
            long faceBytes = (long) faceSize * faceSize * Integer.BYTES;
            for (int layer = 0; layer < SUFFIX_MAPPING.length; layer++) {
                GpuBufferSlice face = buffer.slice(SUFFIX_MAPPING[layer] * faceBytes, faceBytes);
                encoder.copyBufferToTexture(face, 0, 0, faceSize, faceSize, newTexture, 0, 0, faceSize, faceSize, 0, layer);
            }
            encoder.submit();
        } catch (RuntimeException e) {
            if (newView != null) {
                newView.close();
            }
            newTexture.close();
            throw e;
        }

        this.releaseTextures();
        this.texture = newTexture;
        this.textureView = newView;
    }

}
