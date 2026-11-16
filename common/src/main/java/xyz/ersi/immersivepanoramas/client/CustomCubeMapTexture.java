package xyz.ersi.immersivepanoramas.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
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

    synchronized void uploadFaces(NativeImage[] faces) {
        if (faces.length != SUFFIX_MAPPING.length) {
            throw new IllegalArgumentException("A panorama must have six faces");
        }
        int faceSize = faces[0].getWidth();
        if (faceSize != faces[0].getHeight()) {
            throw new IllegalArgumentException("Panorama faces must be square");
        }
        for (NativeImage face : faces) {
            if (face.getWidth() != faceSize || face.getHeight() != faceSize) {
                throw new IllegalArgumentException("Panorama faces must have matching dimensions");
            }
        }

        GpuDevice device = RenderSystem.getDevice();
        int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_CUBEMAP_COMPATIBLE;
        GpuTexture newTexture = device.createTexture(this.id::toString, usage, TextureFormat.RGBA8, faceSize, faceSize, SUFFIX_MAPPING.length, 1);
        GpuTextureView newView = null;
        // match the readback buffer's face order and bottom-up rows
        try (NativeImage flippedFace = new NativeImage(faceSize, faceSize, false)) {
            newView = device.createTextureView(newTexture);
            CommandEncoder encoder = device.createCommandEncoder();
            for (int layer = 0; layer < SUFFIX_MAPPING.length; layer++) {
                faces[SUFFIX_MAPPING[layer]].copyRect(flippedFace, 0, 0, 0, 0, faceSize, faceSize, false, true);
                encoder.writeToTexture(newTexture, flippedFace, 0, layer, 0, 0, faceSize, faceSize, 0, 0);
            }
        } catch (RuntimeException e) {
            if (newView != null) {
                newView.close();
            }
            newTexture.close();
            throw e;
        }

        super.close();
        this.texture = newTexture;
        this.textureView = newView;
    }
}
