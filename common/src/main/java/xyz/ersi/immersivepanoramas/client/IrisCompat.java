package xyz.ersi.immersivepanoramas.client;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

public final class IrisCompat {
    private static final String CAPTURE_UNIFORM = "immersivePanoramasCapturing";
    private static final String PHOTON_VIGNETTE_FUNCTION = "float vignette(vec2 uv)";
    private static final String PHOTON_VIGNETTE_APPLICATION = "scene_color *= vignette(uv);";

    private IrisCompat() {}

    private static Class<?> irisClass(String name) throws ClassNotFoundException {
        try {
            return Class.forName("net.irisshaders.iris." + name);
        } catch (ClassNotFoundException e) {
            return Class.forName("net.coderbot.iris." + name);
        }
    }

    static boolean isShaderPackInUse() {
        try {
            Class<?> irisApi = irisClass("api.v0.IrisApi");
            Object instance = irisApi.getMethod("getInstance").invoke(null);
            Method isShaderPackInUse = irisApi.getMethod("isShaderPackInUse");
            return (boolean) isShaderPackInUse.invoke(instance);
        } catch (ClassNotFoundException e) {
            return false;
        } catch (ReflectiveOperationException | LinkageError e) {
            ModLogger.warn("Could not query Iris shader pack state", e);
            return false;
        }
    }

    public static boolean isRenderingShadowPass() {
        try {
            Class<?> irisApi = irisClass("api.v0.IrisApi");
            Object instance = irisApi.getMethod("getInstance").invoke(null);
            return (boolean) irisApi.getMethod("isRenderingShadowPass").invoke(instance);
        } catch (ClassNotFoundException e) {
            return false;
        } catch (ReflectiveOperationException | LinkageError e) {
            ModLogger.warn("Could not query Iris shadow pass state", e);
            return false;
        }
    }

    public static void registerCaptureUniform(Object uniformHolder) {
        try {
            Class<?> holderType = irisClass("gl.uniform.UniformHolder");
            Class<?> frequencyType = irisClass("gl.uniform.UniformUpdateFrequency");
            Method uniform1b = holderType.getMethod("uniform1b", frequencyType, String.class, BooleanSupplier.class);
            uniform1b.invoke(uniformHolder, frequencyType.getField("PER_FRAME").get(null), CAPTURE_UNIFORM,
                (BooleanSupplier) PanoramaCapture::isCapturing);
        } catch (ReflectiveOperationException | LinkageError e) {
            ModLogger.warn("Could not register the Iris panorama capture uniform", e);
        }
    }

    public static String patchPhotonVignette(String source) {
        if (!source.contains(PHOTON_VIGNETTE_FUNCTION) || !source.contains(PHOTON_VIGNETTE_APPLICATION)) {
            return source;
        }

        return source.replace(PHOTON_VIGNETTE_FUNCTION, "uniform bool " + CAPTURE_UNIFORM + "; " + PHOTON_VIGNETTE_FUNCTION)
            .replace(PHOTON_VIGNETTE_APPLICATION, "if (!" + CAPTURE_UNIFORM + ") " + PHOTON_VIGNETTE_APPLICATION);
    }
}
