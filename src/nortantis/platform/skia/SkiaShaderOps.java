package nortantis.platform.skia;

import java.util.Map;

import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.PixelWriter;
import nortantis.util.Helper;
import nortantis.util.ImageHelper.ColorifyAlgorithm;
import org.jetbrains.skia.*;

/**
 * Provides high-performance image operations using Skia shaders and blend modes. These operations use GPU acceleration when available via
 * GPUExecutor, falling back to SIMD-optimized CPU rendering otherwise.
 *
 * <h2>Alpha Handling in Shaders</h2>
 * <p>
 * <b>IMPORTANT:</b> When writing SkSL shaders, be aware of premultiplied alpha:
 * <ul>
 * <li>Source images (SkiaImage) are stored as UNPREMUL (see {@link SkiaImage#getImageInfoForType})</li>
 * <li>GPU render targets require PREMUL alpha (see {@link #createGPUSurfaceOnGPUThread})</li>
 * <li>When shaders sample images via {@code image.eval(coord)}, Skia returns <b>premultiplied</b> values</li>
 * <li>To get straight RGB for blending, you must unpremultiply: {@code imgColor.a > 0 ? imgColor.rgb / imgColor.a : half3(0)}</li>
 * <li>Before returning from a shader writing to GPU surface, repremultiply: {@code return half4(rgb * alpha, alpha)}</li>
 * </ul>
 * </p>
 */
public class SkiaShaderOps
{
	private static final Object effectLock = new Object();

	// ==================== Surface Creation Helper ====================

	/**
	 * Returns true if the given dimensions are suitable for GPU rendering. Very large images that exceed the GPU's maximum texture size
	 * should use CPU rendering.
	 */
	private static boolean canUseGPUForSize(int width, int height)
	{
		int maxTextureSize = GPUExecutor.getInstance().getMaxTextureSize();
		return width <= maxTextureSize && height <= maxTextureSize;
	}

	/**
	 * Creates a CPU raster surface for shader operations.
	 */
	private static Surface createCPUSurface(int width, int height)
	{
		return Surface.Companion.makeRaster(new ImageInfo(width, height, ColorType.Companion.getN32(), ColorAlphaType.UNPREMUL, null));
	}

	/**
	 * Creates a GPU surface. Must be called from the GPU thread. GPU render targets require PREMUL alpha type. Returns null if surface
	 * creation fails.
	 */
	private static Surface createGPUSurfaceOnGPUThread(int width, int height)
	{
		DirectContext ctx = GPUExecutor.getInstance().getContext();
		if (ctx == null)
		{
			return null;
		}
		return Surface.Companion.makeRenderTarget(ctx, false, new ImageInfo(width, height, ColorType.Companion.getN32(), ColorAlphaType.PREMUL, null));
	}

	/**
	 * Reads pixels from a surface into a new Bitmap. For GPU surfaces, flushes pending commands before reading. Returns an UNPREMUL bitmap
	 * for consistency with the rest of the codebase.
	 */
	private static Bitmap readPixelsToBitmap(Surface surface, int width, int height)
	{
		// Flush any pending GPU commands to ensure the surface is up-to-date
		surface.flushAndSubmit(true); // true = sync

		// Read directly to UNPREMUL bitmap (Skia handles the conversion from PREMUL surface)
		Bitmap resultBitmap = new Bitmap();
		ImageInfo dstInfo = new ImageInfo(width, height, ColorType.Companion.getN32(), ColorAlphaType.UNPREMUL, null);
		resultBitmap.allocPixels(dstInfo);

		// Try reading directly from surface - Skia converts PREMUL surface -> UNPREMUL bitmap
		boolean success = surface.readPixels(resultBitmap, 0, 0);
		return resultBitmap;
	}

	/**
	 * Creates a SkiaImage from a shader result surface. In GPU-only mode for non-grayscale results, hands off the surface directly to avoid
	 * GPU→CPU readback. Otherwise reads pixels to a bitmap.
	 *
	 * @param surface
	 *            The surface containing shader results. Ownership is transferred if GPU-only mode is used; otherwise the caller should
	 *            close it.
	 * @param width
	 *            Surface width
	 * @param height
	 *            Surface height
	 * @param resultType
	 *            The ImageType for the result
	 * @param useGPU
	 *            Whether the surface is a GPU surface
	 * @return A new SkiaImage, and whether the surface ownership was transferred (caller should not close it)
	 */
	private static ImageFromSurface createImageFromSurface(Surface surface, int width, int height, ImageType resultType, boolean useGPU)
	{
		// In GPU-only mode, hand off the surface directly for non-grayscale results
		if (useGPU && GPUExecutor.isGpuOnlyMode() && resultType != ImageType.Grayscale8Bit && resultType != ImageType.Binary && resultType != ImageType.Grayscale16Bit)
		{
			return new ImageFromSurface(new SkiaImage(surface, width, height, resultType), true);
		}

		Bitmap resultBitmap = readPixelsToBitmap(surface, width, height);
		return new ImageFromSurface(new SkiaImage(resultBitmap, resultType), false);
	}

	/**
	 * Helper record to return both an image and whether the surface ownership was transferred.
	 */
	private record ImageFromSurface(SkiaImage image, boolean surfaceTransferred)
	{
	}

	// ==================== maskWithImage ====================

	// SkSL shader for mask blending: result = mix(image2, image1, mask)
	// Note: Skia shaders work in premultiplied color space - image.eval() returns premul values
	// GPU surfaces also expect premultiplied output, so we don't need extra conversion
	private static final String MASK_WITH_IMAGE_SKSL = """
			uniform shader image1;
			uniform shader image2;
			uniform shader mask;

			half4 main(float2 coord) {
			    half4 c1 = image1.eval(coord);
			    half4 c2 = image2.eval(coord);
			    half m = mask.eval(coord).r;
			    // Both inputs and output are premultiplied - mix works correctly in premul space
			    return mix(c2, c1, m);
			}
			""";

	private static RuntimeEffect maskWithImageEffect;

	private static RuntimeEffect getMaskWithImageEffect()
	{
		if (maskWithImageEffect == null)
		{
			synchronized (effectLock)
			{
				if (maskWithImageEffect == null)
				{
					maskWithImageEffect = RuntimeEffect.Companion.makeForShader(MASK_WITH_IMAGE_SKSL);
				}
			}
		}
		return maskWithImageEffect;
	}

	/**
	 * Blends two images using a grayscale mask. result = mask * image1 + (1 - mask) * image2
	 */
	public static Image maskWithImage(Image image1, Image image2, Image mask)
	{
		SkiaImage skImage1 = (SkiaImage) image1;
		SkiaImage skImage2 = (SkiaImage) image2;
		SkiaImage skMask = (SkiaImage) mask;

		int width = image1.getWidth();
		int height = image1.getHeight();
		ImageType resultType = (image1.hasAlpha() || image2.hasAlpha()) ? ImageType.ARGB : ImageType.RGB;

		// Check if we should use GPU (also check size limits to avoid crashes with large images)
		if (isGPUAccelerated() && canUseGPUForSize(width, height))
		{
			return GPUExecutor.getInstance().submit(() -> maskWithImageImpl(skImage1, skImage2, skMask, width, height, resultType, true));
		}
		else
		{
			return maskWithImageImpl(skImage1, skImage2, skMask, width, height, resultType, false);
		}
	}

	private static Image maskWithImageImpl(SkiaImage skImage1, SkiaImage skImage2, SkiaImage skMask, int width, int height, ImageType resultType, boolean useGPU)
	{
		org.jetbrains.skia.Image skiImage1 = skImage1.getSkiaImage();
		org.jetbrains.skia.Image skiImage2 = skImage2.getSkiaImage();
		org.jetbrains.skia.Image skiMask = skMask.getSkiaImage();

		Matrix33 identity = Matrix33.Companion.getIDENTITY();

		Shader shader1 = skiImage1.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);
		Shader shader2 = skiImage2.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);
		Shader shaderMask = skiMask.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);

		RuntimeEffect effect = getMaskWithImageEffect();
		RuntimeShaderBuilder builder = new RuntimeShaderBuilder(effect);
		builder.child("image1", shader1);
		builder.child("image2", shader2);
		builder.child("mask", shaderMask);
		Shader resultShader = builder.makeShader(identity);

		Surface surface = useGPU ? createGPUSurfaceOnGPUThread(width, height) : createCPUSurface(width, height);
		Canvas canvas = surface.getCanvas();

		Paint paint = new Paint();
		paint.setShader(resultShader);
		canvas.drawRect(Rect.makeWH(width, height), paint);

		ImageFromSurface result = createImageFromSurface(surface, width, height, resultType, useGPU);

		// Clean up
		if (!result.surfaceTransferred())
		{
			surface.close();
		}
		paint.close();
		resultShader.close();
		shader1.close();
		shader2.close();
		shaderMask.close();
		builder.close();

		return result.image();
	}

	// ==================== maskWithColor ====================

	// SkSL shader for blending image with solid color using mask.
	// Matches the logic in ImageHelper.maskWithColorInRegion:
	// - Creates an overlay with (color.rgb, overlayAlpha) where overlayAlpha = 255 - maskLevel
	// - maskLevel = mask * colorAlpha (or inverted: 255 - mask * colorAlpha)
	// - Composites overlay onto image using standard SRC_OVER blending
	// Note: Skia shaders work in premultiplied color space - image.eval() returns premul values
	private static final String MASK_WITH_COLOR_SKSL = """
			uniform shader image;
			uniform shader mask;
			uniform half3 colorRGB;
			uniform half colorAlpha;
			uniform half invertMask;

			half4 main(float2 coord) {
			    half4 imgColor = image.eval(coord);  // premultiplied
			    half m = mask.eval(coord).r;

			    // Calculate blend factor matching Java logic:
			    // maskLevel = m * colorAlpha (in 0-1 range here)
			    // overlayAlpha = 1 - maskLevel (non-inverted) or maskLevel (inverted)
			    // blendFactor = overlayAlpha (how much of color to show)
			    half blendFactor;
			    if (invertMask > 0.5) {
			        blendFactor = m * colorAlpha;
			    } else {
			        blendFactor = 1.0 - m * colorAlpha;
			    }

			    // Unpremultiply image color to get straight RGB for blending
			    half3 imgRGB = imgColor.a > 0.0 ? imgColor.rgb / imgColor.a : half3(0.0);

			    // Blend in straight alpha space
			    half3 resultRGB = mix(imgRGB, colorRGB, blendFactor);
			    half resultA = imgColor.a;

			    // Output premultiplied for GPU surface
			    return half4(resultRGB * resultA, resultA);
			}
			""";

	private static RuntimeEffect maskWithColorEffect;

	private static RuntimeEffect getMaskWithColorEffect()
	{
		if (maskWithColorEffect == null)
		{
			synchronized (effectLock)
			{
				if (maskWithColorEffect == null)
				{
					maskWithColorEffect = RuntimeEffect.Companion.makeForShader(MASK_WITH_COLOR_SKSL);
					if (maskWithColorEffect == null)
					{
						throw new RuntimeException("Failed to compile MASK_WITH_COLOR_SKSL shader");
					}
				}
			}
		}
		return maskWithColorEffect;
	}

	/**
	 * Blends an image with a solid color using a grayscale mask. Matches the behavior of ImageHelper.maskWithColorInRegion: - mask=0
	 * (black), not inverted -> fully show color - mask=1 (white), not inverted -> fully show image - The color's alpha modulates the mask
	 * effect
	 */
	public static Image maskWithColor(Image image, Color color, Image mask, boolean invertMask)
	{
		SkiaImage skImage = (SkiaImage) image;
		SkiaImage skMask = (SkiaImage) mask;

		int width = image.getWidth();
		int height = image.getHeight();
		ImageType resultType = image.hasAlpha() ? ImageType.ARGB : ImageType.RGB;

		// Extract color components before lambda
		float r = color.getRed() / 255f;
		float g = color.getGreen() / 255f;
		float b = color.getBlue() / 255f;
		float a = color.getAlpha() / 255f;

		// Check if we should use GPU (also check size limits to avoid crashes with large images)
		if (isGPUAccelerated() && canUseGPUForSize(width, height))
		{
			return GPUExecutor.getInstance().submit(() -> maskWithColorImpl(skImage, skMask, width, height, r, g, b, a, invertMask, resultType, true));
		}
		else
		{
			return maskWithColorImpl(skImage, skMask, width, height, r, g, b, a, invertMask, resultType, false);
		}
	}

	private static Image maskWithColorImpl(SkiaImage skImage, SkiaImage skMask, int width, int height, float r, float g, float b, float a, boolean invertMask, ImageType resultType, boolean useGPU)
	{
		org.jetbrains.skia.Image skiImage = skImage.getSkiaImage();
		org.jetbrains.skia.Image skiMask = skMask.getSkiaImage();

		Matrix33 identity = Matrix33.Companion.getIDENTITY();

		Shader imageShader = skiImage.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);
		Shader maskShader = skiMask.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);

		RuntimeEffect effect = getMaskWithColorEffect();
		RuntimeShaderBuilder builder = new RuntimeShaderBuilder(effect);
		builder.child("image", imageShader);
		builder.child("mask", maskShader);
		builder.uniform("colorRGB", r, g, b);
		builder.uniform("colorAlpha", a);
		builder.uniform("invertMask", invertMask ? 1f : 0f);
		Shader resultShader = builder.makeShader(identity);

		Surface surface = useGPU ? createGPUSurfaceOnGPUThread(width, height) : createCPUSurface(width, height);
		Canvas canvas = surface.getCanvas();

		Paint paint = new Paint();
		paint.setShader(resultShader);
		canvas.drawRect(Rect.makeWH(width, height), paint);

		ImageFromSurface result = createImageFromSurface(surface, width, height, resultType, useGPU);

		// Clean up
		if (!result.surfaceTransferred())
		{
			surface.close();
		}
		paint.close();
		resultShader.close();
		imageShader.close();
		maskShader.close();
		builder.close();

		return result.image();
	}

	// ==================== setAlphaFromMask ====================

	// SkSL shader to set alpha from grayscale mask
	// Note: Skia shaders work in premultiplied color space - image.eval() returns premul values
	private static final String SET_ALPHA_FROM_MASK_SKSL = """
			uniform shader image;
			uniform shader mask;
			uniform half invertMask;

			half4 main(float2 coord) {
			    half4 c = image.eval(coord);  // premultiplied: rgb is already multiplied by c.a
			    half m = mask.eval(coord).r;
			    if (invertMask > 0.5) {
			        m = 1.0 - m;
			    }
			    // Take minimum of mask and existing alpha
			    half newAlpha = min(m, c.a);

			    // Unpremultiply, then repremultiply with new alpha
			    // c.rgb = originalRGB * c.a, so originalRGB = c.rgb / c.a
			    // newPremul = originalRGB * newAlpha = (c.rgb / c.a) * newAlpha
			    // Simplify: newPremul = c.rgb * (newAlpha / c.a)
			    half3 newRGB = c.a > 0.0 ? c.rgb * (newAlpha / c.a) : half3(0.0);
			    return half4(newRGB, newAlpha);
			}
			""";

	private static RuntimeEffect setAlphaFromMaskEffect;

	private static RuntimeEffect getSetAlphaFromMaskEffect()
	{
		if (setAlphaFromMaskEffect == null)
		{
			synchronized (effectLock)
			{
				if (setAlphaFromMaskEffect == null)
				{
					setAlphaFromMaskEffect = RuntimeEffect.Companion.makeForShader(SET_ALPHA_FROM_MASK_SKSL);
				}
			}
		}
		return setAlphaFromMaskEffect;
	}

	/**
	 * Sets alpha channel from a grayscale mask. The result alpha is min(mask, originalAlpha).
	 */
	public static Image setAlphaFromMask(Image image, Image alphaMask, boolean invertMask)
	{
		SkiaImage skImage = (SkiaImage) image;
		SkiaImage skMask = (SkiaImage) alphaMask;

		int width = image.getWidth();
		int height = image.getHeight();

		// Check if we should use GPU (also check size limits to avoid crashes with large images)
		if (isGPUAccelerated() && canUseGPUForSize(width, height))
		{
			return GPUExecutor.getInstance().submit(() -> setAlphaFromMaskImpl(skImage, skMask, width, height, invertMask, true));
		}
		else
		{
			return setAlphaFromMaskImpl(skImage, skMask, width, height, invertMask, false);
		}
	}

	private static Image setAlphaFromMaskImpl(SkiaImage skImage, SkiaImage skMask, int width, int height, boolean invertMask, boolean useGPU)
	{
		org.jetbrains.skia.Image skiImage = skImage.getSkiaImage();
		org.jetbrains.skia.Image skiMask = skMask.getSkiaImage();

		Matrix33 identity = Matrix33.Companion.getIDENTITY();

		Shader imageShader = skiImage.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);
		Shader maskShader = skiMask.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);

		RuntimeEffect effect = getSetAlphaFromMaskEffect();
		RuntimeShaderBuilder builder = new RuntimeShaderBuilder(effect);
		builder.child("image", imageShader);
		builder.child("mask", maskShader);
		builder.uniform("invertMask", invertMask ? 1f : 0f);
		Shader resultShader = builder.makeShader(identity);

		Surface surface = useGPU ? createGPUSurfaceOnGPUThread(width, height) : createCPUSurface(width, height);
		Canvas canvas = surface.getCanvas();

		Paint paint = new Paint();
		paint.setShader(resultShader);
		canvas.drawRect(Rect.makeWH(width, height), paint);

		ImageFromSurface result = createImageFromSurface(surface, width, height, ImageType.ARGB, useGPU);

		// Clean up
		if (!result.surfaceTransferred())
		{
			surface.close();
		}
		paint.close();
		resultShader.close();
		imageShader.close();
		maskShader.close();
		builder.close();

		return result.image();
	}

	// ==================== colorify ====================

	// SkSL shader for colorify algorithm2
	// Takes grayscale image and applies HSB color transformation
	// Note: Grayscale images have alpha=1.0, so premul gray value = straight gray value
	private static final String COLORIFY_ALG2_SKSL = """
			uniform shader image;
			uniform half3 hsb;  // hue, saturation, brightness
			uniform half alpha;

			// HSB to RGB conversion
			half3 hsb2rgb(half h, half s, half b) {
			    half c = b * s;
			    half hPrime = h * 6.0;
			    half x = c * (1.0 - abs(mod(hPrime, 2.0) - 1.0));
			    half3 rgb;
			    if (hPrime < 1.0) rgb = half3(c, x, 0.0);
			    else if (hPrime < 2.0) rgb = half3(x, c, 0.0);
			    else if (hPrime < 3.0) rgb = half3(0.0, c, x);
			    else if (hPrime < 4.0) rgb = half3(0.0, x, c);
			    else if (hPrime < 5.0) rgb = half3(x, 0.0, c);
			    else rgb = half3(c, 0.0, x);
			    half m = b - c;
			    return rgb + m;
			}

			half4 main(float2 coord) {
			    half gray = image.eval(coord).r;  // grayscale, premul doesn't affect it
			    half I = hsb.z * 255.0;
			    half overlay = ((I / 255.0) * (I + (2.0 * gray) * (255.0 - I))) / 255.0;
			    half3 rgb = hsb2rgb(hsb.x, hsb.y, overlay);
			    // Output premultiplied for GPU surface
			    return half4(rgb * alpha, alpha);
			}
			""";

	// SkSL shader for colorify algorithm3
	// Output is premultiplied (rgb * alpha) for GPU render targets
	private static final String COLORIFY_ALG3_SKSL = """
			uniform shader image;
			uniform half3 hsb;
			uniform half alpha;

			half3 hsb2rgb(half h, half s, half b) {
			    half c = b * s;
			    half hPrime = h * 6.0;
			    half x = c * (1.0 - abs(mod(hPrime, 2.0) - 1.0));
			    half3 rgb;
			    if (hPrime < 1.0) rgb = half3(c, x, 0.0);
			    else if (hPrime < 2.0) rgb = half3(x, c, 0.0);
			    else if (hPrime < 3.0) rgb = half3(0.0, c, x);
			    else if (hPrime < 4.0) rgb = half3(0.0, x, c);
			    else if (hPrime < 5.0) rgb = half3(x, 0.0, c);
			    else rgb = half3(c, 0.0, x);
			    half m = b - c;
			    return rgb + m;
			}

			half4 main(float2 coord) {
			    half gray = image.eval(coord).r;
			    half resultLevel;
			    if (hsb.z < 0.5) {
			        resultLevel = gray * (hsb.z * 2.0);
			    } else {
			        half range = (1.0 - hsb.z) * 2.0;
			        resultLevel = range * gray + (1.0 - range);
			    }
			    half3 rgb = hsb2rgb(hsb.x, hsb.y, resultLevel);
			    // Output premultiplied for GPU surface
			    return half4(rgb * alpha, alpha);
			}
			""";

	// SkSL shader for colorify solidColor (just outputs the color)
	// Output is premultiplied (rgb * alpha) for GPU render targets
	private static final String COLORIFY_SOLID_SKSL = """
			uniform half4 color;

			half4 main(float2 coord) {
			    // Output premultiplied for GPU surface
			    return half4(color.rgb * color.a, color.a);
			}
			""";

	private static RuntimeEffect colorifyAlg2Effect;
	private static RuntimeEffect colorifyAlg3Effect;
	private static RuntimeEffect colorifySolidEffect;

	private static RuntimeEffect getColorifyAlg2Effect()
	{
		if (colorifyAlg2Effect == null)
		{
			synchronized (effectLock)
			{
				if (colorifyAlg2Effect == null)
				{
					colorifyAlg2Effect = RuntimeEffect.Companion.makeForShader(COLORIFY_ALG2_SKSL);
				}
			}
		}
		return colorifyAlg2Effect;
	}

	private static RuntimeEffect getColorifyAlg3Effect()
	{
		if (colorifyAlg3Effect == null)
		{
			synchronized (effectLock)
			{
				if (colorifyAlg3Effect == null)
				{
					colorifyAlg3Effect = RuntimeEffect.Companion.makeForShader(COLORIFY_ALG3_SKSL);
				}
			}
		}
		return colorifyAlg3Effect;
	}

	private static RuntimeEffect getColorifySolidEffect()
	{
		if (colorifySolidEffect == null)
		{
			synchronized (effectLock)
			{
				if (colorifySolidEffect == null)
				{
					colorifySolidEffect = RuntimeEffect.Companion.makeForShader(COLORIFY_SOLID_SKSL);
				}
			}
		}
		return colorifySolidEffect;
	}

	/**
	 * Creates a colored image from a grayscale one using HSB color transformation.
	 */
	public static Image colorify(Image image, Color color, ColorifyAlgorithm how, boolean forceAddAlpha)
	{
		if (how == ColorifyAlgorithm.none)
		{
			return image;
		}

		SkiaImage skImage = (SkiaImage) image;
		int width = image.getWidth();
		int height = image.getHeight();

		float[] hsb = color.getHSB();
		float alpha = color.getAlpha() / 255f;
		float r = color.getRed() / 255f;
		float g = color.getGreen() / 255f;
		float b = color.getBlue() / 255f;

		ImageType resultType = forceAddAlpha || color.hasTransparency() ? ImageType.ARGB : ImageType.RGB;

		// Check if we should use GPU (also check size limits to avoid crashes with large images)
		if (isGPUAccelerated() && canUseGPUForSize(width, height))
		{
			return GPUExecutor.getInstance().submit(() -> colorifyImpl(skImage, width, height, how, hsb, alpha, r, g, b, resultType, true));
		}
		else
		{
			return colorifyImpl(skImage, width, height, how, hsb, alpha, r, g, b, resultType, false);
		}
	}

	private static Image colorifyImpl(SkiaImage skImage, int width, int height, ColorifyAlgorithm how, float[] hsb, float alpha, float r, float g, float b, ImageType resultType, boolean useGPU)
	{
		org.jetbrains.skia.Image skiImage = skImage.getSkiaImage();
		Matrix33 identity = Matrix33.Companion.getIDENTITY();
		Shader imageShader = skiImage.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);

		RuntimeEffect effect;
		RuntimeShaderBuilder builder;

		if (how == ColorifyAlgorithm.algorithm2)
		{
			effect = getColorifyAlg2Effect();
			builder = new RuntimeShaderBuilder(effect);
			builder.child("image", imageShader);
			builder.uniform("hsb", hsb[0], hsb[1], hsb[2]);
			builder.uniform("alpha", alpha);
		}
		else if (how == ColorifyAlgorithm.algorithm3)
		{
			effect = getColorifyAlg3Effect();
			builder = new RuntimeShaderBuilder(effect);
			builder.child("image", imageShader);
			builder.uniform("hsb", hsb[0], hsb[1], hsb[2]);
			builder.uniform("alpha", alpha);
		}
		else if (how == ColorifyAlgorithm.solidColor)
		{
			effect = getColorifySolidEffect();
			builder = new RuntimeShaderBuilder(effect);
			builder.uniform("color", r, g, b, alpha);
		}
		else
		{
			imageShader.close();
			throw new IllegalArgumentException("Unrecognized colorify algorithm: " + how);
		}

		Shader resultShader = builder.makeShader(identity);

		Surface surface = useGPU ? createGPUSurfaceOnGPUThread(width, height) : createCPUSurface(width, height);
		Canvas canvas = surface.getCanvas();

		Paint paint = new Paint();
		paint.setShader(resultShader);
		canvas.drawRect(Rect.makeWH(width, height), paint);

		ImageFromSurface result = createImageFromSurface(surface, width, height, resultType, useGPU);

		// Clean up
		if (!result.surfaceTransferred())
		{
			surface.close();
		}
		paint.close();
		resultShader.close();
		imageShader.close();
		builder.close();

		return result.image();
	}

	// ==================== convertToGrayscale ====================

	// SkSL shader for RGB to grayscale conversion using standard luminance formula
	private static final String GRAYSCALE_SKSL = """
			uniform shader image;

			half4 main(float2 coord) {
			    half4 c = image.eval(coord);
			    // Standard luminance formula (ITU-R BT.601)
			    half gray = 0.299 * c.r + 0.587 * c.g + 0.114 * c.b;
			    return half4(gray, gray, gray, c.a);
			}
			""";

	private static RuntimeEffect grayscaleEffect;

	private static RuntimeEffect getGrayscaleEffect()
	{
		if (grayscaleEffect == null)
		{
			synchronized (effectLock)
			{
				if (grayscaleEffect == null)
				{
					grayscaleEffect = RuntimeEffect.Companion.makeForShader(GRAYSCALE_SKSL);
				}
			}
		}
		return grayscaleEffect;
	}

	/**
	 * Converts an image to grayscale using the standard luminance formula. This produces consistent results on both CPU and GPU.
	 */
	public static Image convertToGrayscale(Image image)
	{
		SkiaImage skImage = (SkiaImage) image;
		int width = image.getWidth();
		int height = image.getHeight();

		if (isGPUAccelerated() && canUseGPUForSize(width, height))
		{
			return GPUExecutor.getInstance().submit(() -> convertToGrayscaleImpl(skImage, width, height, true));
		}
		else
		{
			return convertToGrayscaleImpl(skImage, width, height, false);
		}
	}

	private static Image convertToGrayscaleImpl(SkiaImage skImage, int width, int height, boolean useGPU)
	{
		org.jetbrains.skia.Image skiImage = skImage.getSkiaImage();
		Matrix33 identity = Matrix33.Companion.getIDENTITY();
		Shader imageShader = skiImage.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);

		RuntimeEffect effect = getGrayscaleEffect();
		RuntimeShaderBuilder builder = new RuntimeShaderBuilder(effect);
		builder.child("image", imageShader);
		Shader resultShader = builder.makeShader(identity);

		Surface surface = useGPU ? createGPUSurfaceOnGPUThread(width, height) : createCPUSurface(width, height);
		Canvas canvas = surface.getCanvas();

		Paint paint = new Paint();
		paint.setShader(resultShader);
		canvas.drawRect(Rect.makeWH(width, height), paint);

		// Read to a GRAY_8 bitmap for proper grayscale storage
		surface.flushAndSubmit(true);
		Bitmap resultBitmap = new Bitmap();
		ImageInfo grayInfo = new ImageInfo(width, height, ColorType.GRAY_8, ColorAlphaType.OPAQUE, null);
		resultBitmap.allocPixels(grayInfo);
		surface.readPixels(resultBitmap, 0, 0);

		// Clean up
		surface.close();
		paint.close();
		resultShader.close();
		imageShader.close();
		builder.close();

		return new SkiaImage(resultBitmap, ImageType.Grayscale8Bit);
	}

	// ==================== maskWithMultipleColors ====================

	// Maximum palette dimensions for region color lookup (256 * 128 = 32768 slots, covering max region ID of 32000)
	// Actual palette size is calculated dynamically based on the highest region ID
	private static final int MAX_PALETTE_WIDTH = 256;
	private static final int MAX_PALETTE_HEIGHT = 128;

	// SkSL shader for blending an image with multiple colors based on region IDs
	// The colorIndexes image encodes region IDs as RGB (r << 16 | g << 8 | b)
	// The palette is a 2D texture where color for region ID r is at (r % paletteWidth, r / paletteWidth)
	// Note: shader.eval() expects pixel coordinates, not normalized coordinates
	// Note: Skia shaders work in premultiplied color space - image.eval() returns premul values
	private static final String MASK_WITH_MULTIPLE_COLORS_SKSL = """
			uniform shader image;        // Input image (RGB or grayscale)
			uniform shader colorIndexes; // Region IDs encoded as RGB
			uniform shader mask;         // Grayscale blend mask
			uniform shader palette;      // 2D color palette (dynamic size)
			uniform half invertMask;
			uniform float paletteWidth;  // Width of palette texture for coordinate calculation

			half4 main(float2 coord) {
			    half4 imgColor = image.eval(coord);
			    half3 originalRGB = imgColor.rgb;

			    // Decode region ID from colorIndexes (r << 16 | g << 8 | b)
			    half4 indexColor = colorIndexes.eval(coord);
			    float regionId = floor(indexColor.r * 255.0 + 0.5) * 65536.0
			                   + floor(indexColor.g * 255.0 + 0.5) * 256.0
			                   + floor(indexColor.b * 255.0 + 0.5);

			    // Calculate 2D palette coordinates in pixel space
			    float py = floor(regionId / paletteWidth);
			    float px = regionId - py * paletteWidth;
			    float2 paletteCoord = float2(px + 0.5, py + 0.5);
			    half4 regionColor = palette.eval(paletteCoord);

			    // If no color for this region (alpha = 0), return original color
			    if (regionColor.a < 0.01) {
			        return half4(originalRGB, 1.0);
			    }

			    // Get mask value
			    half m = mask.eval(coord).r;
			    if (invertMask > 0.5) {
			        m = 1.0 - m;
			    }

			    // Blend: m=1 (white mask) -> show original, m=0 (black mask) -> show region color
			    half3 blended = mix(regionColor.rgb, originalRGB, m);
			    return half4(blended, 1.0);
			}
			""";

	private static RuntimeEffect maskWithMultipleColorsEffect;

	private static RuntimeEffect getMaskWithMultipleColorsEffect()
	{
		if (maskWithMultipleColorsEffect == null)
		{
			synchronized (effectLock)
			{
				if (maskWithMultipleColorsEffect == null)
				{
					maskWithMultipleColorsEffect = RuntimeEffect.Companion.makeForShader(MASK_WITH_MULTIPLE_COLORS_SKSL);
					if (maskWithMultipleColorsEffect == null)
					{
						throw new RuntimeException("Failed to compile MASK_WITH_MULTIPLE_COLORS_SKSL shader");
					}
				}
			}
		}
		return maskWithMultipleColorsEffect;
	}

	/**
	 * Blends a grayscale image with multiple colors based on region IDs and a mask. Each pixel's region ID is decoded from the colorIndexes
	 * image (RGB encoded as r<<16|g<<8|b). The corresponding color is looked up from the colors map and blended based on the mask.
	 *
	 * @param image
	 *            Grayscale source image
	 * @param colors
	 *            Map of region ID to color
	 * @param colorIndexes
	 *            RGB image encoding region IDs
	 * @param mask
	 *            Grayscale mask controlling blend (white=original, black=region color)
	 * @param invertMask
	 *            If true, inverts the mask
	 * @return Blended RGB image
	 */
	public static Image maskWithMultipleColors(Image image, Map<Integer, Color> colors, Image colorIndexes, Image mask, boolean invertMask)
	{
		SkiaImage skImage = (SkiaImage) image;
		SkiaImage skColorIndexes = (SkiaImage) colorIndexes;
		SkiaImage skMask = (SkiaImage) mask;

		int width = image.getWidth();
		int height = image.getHeight();
		ImageType resultType = image.getType();

		// Calculate optimal palette dimensions based on region IDs
		int[] paletteDims = calculatePaletteDimensions(colors);
		int paletteWidth = paletteDims[0];
		int paletteHeight = paletteDims[1];

		// Create palette texture using Image wrapper (same pattern as colorifyMulti)
		Image palette = createPaletteTexture(colors, paletteWidth, paletteHeight);
		SkiaImage skPalette = (SkiaImage) palette;

		try
		{
			// Check if we should use GPU (also check size limits to avoid crashes with large images)
			if (isGPUAccelerated() && canUseGPUForSize(width, height))
			{
				return GPUExecutor.getInstance().submit(() -> maskWithMultipleColorsImpl(skImage, skColorIndexes, skMask, skPalette, width, height, invertMask, paletteWidth, resultType, true));
			}
			else
			{
				return maskWithMultipleColorsImpl(skImage, skColorIndexes, skMask, skPalette, width, height, invertMask, paletteWidth, resultType, false);
			}
		}
		finally
		{
			palette.close();
		}
	}

	private static Image maskWithMultipleColorsImpl(SkiaImage skImage, SkiaImage skColorIndexes, SkiaImage skMask, SkiaImage skPalette, int width, int height, boolean invertMask, int paletteWidth,
			ImageType resultType, boolean useGPU)
	{
		org.jetbrains.skia.Image skiImage = skImage.getSkiaImage();
		org.jetbrains.skia.Image skiColorIndexes = skColorIndexes.getSkiaImage();
		org.jetbrains.skia.Image skiMask = skMask.getSkiaImage();
		org.jetbrains.skia.Image skiPalette = skPalette.getSkiaImage();

		Matrix33 identity = Matrix33.Companion.getIDENTITY();

		Shader imageShader = skiImage.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);
		Shader colorIndexesShader = skiColorIndexes.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);
		Shader maskShader = skiMask.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);
		Shader paletteShader = skiPalette.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);

		RuntimeEffect effect = getMaskWithMultipleColorsEffect();
		RuntimeShaderBuilder builder = new RuntimeShaderBuilder(effect);
		builder.child("image", imageShader);
		builder.child("colorIndexes", colorIndexesShader);
		builder.child("mask", maskShader);
		builder.child("palette", paletteShader);
		builder.uniform("invertMask", invertMask ? 1f : 0f);
		builder.uniform("paletteWidth", (float) paletteWidth);
		Shader resultShader = builder.makeShader(identity);

		Surface surface = useGPU ? createGPUSurfaceOnGPUThread(width, height) : createCPUSurface(width, height);
		Canvas canvas = surface.getCanvas();

		Paint paint = new Paint();
		paint.setShader(resultShader);
		canvas.drawRect(Rect.makeWH(width, height), paint);

		ImageFromSurface result = createImageFromSurface(surface, width, height, resultType, useGPU);

		// Clean up
		if (!result.surfaceTransferred())
		{
			surface.close();
		}
		paint.close();
		resultShader.close();
		imageShader.close();
		colorIndexesShader.close();
		maskShader.close();
		paletteShader.close();
		builder.close();

		return result.image();
	}

	// Minimum palette width to avoid GPU texture issues with very small textures
	private static final int MIN_PALETTE_WIDTH = 16;

	/**
	 * Calculates optimal palette dimensions based on the highest region ID. Returns {width, height}. For small region counts, creates a
	 * compact palette. For larger counts, uses a 2D layout with width up to MAX_PALETTE_WIDTH.
	 */
	private static int[] calculatePaletteDimensions(Map<Integer, Color> colors)
	{
		if (colors.isEmpty())
		{
			return new int[] { MIN_PALETTE_WIDTH, 1 };
		}

		int highestKey = Helper.maxItem(colors.keySet());
		int numSlots = highestKey + 1; // Need slots 0 through highestKey

		// For small palettes, use a single row with minimum width
		if (numSlots <= MAX_PALETTE_WIDTH)
		{
			int width = Math.max(numSlots, MIN_PALETTE_WIDTH);
			return new int[] { width, 1 };
		}

		// For larger palettes, use 2D layout with MAX_PALETTE_WIDTH columns
		int width = MAX_PALETTE_WIDTH;
		int height = (numSlots + width - 1) / width; // Ceiling division
		height = Math.min(height, MAX_PALETTE_HEIGHT);
		return new int[] { width, height };
	}

	/**
	 * Creates a 2D palette texture from the colors map using the Image wrapper. Region ID r is stored at pixel position (r % width, r /
	 * width). Used by colorifyMulti and maskWithMultipleColorsInPlace methods.
	 *
	 * @param colors
	 *            Map of region ID to color
	 * @param width
	 *            Palette width (used for coordinate calculation)
	 * @param height
	 *            Palette height
	 * @return The created palette image
	 */
	private static Image createPaletteTexture(Map<Integer, Color> colors, int width, int height)
	{
		Image palette = Image.create(width, height, ImageType.ARGB);
		try (PixelWriter writer = palette.createPixelWriter())
		{
			// Initialize with transparent (alpha=0) to indicate missing regions
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					writer.setRGB(x, y, 0, 0, 0, 0);
				}
			}

			// Fill in the colors from the map
			int maxSlots = width * height;
			for (Map.Entry<Integer, Color> entry : colors.entrySet())
			{
				int regionId = entry.getKey();
				if (regionId < 0 || regionId >= maxSlots)
				{
					continue; // Skip invalid region IDs
				}
				Color color = entry.getValue();
				int px = regionId % width;
				int py = regionId / width;
				writer.setRGB(px, py, color.getRed(), color.getGreen(), color.getBlue(), 255);
			}
		}
		return palette;
	}

	// ==================== colorifyMulti ====================

	// SkSL shader for colorifying with multiple colors (algorithm2)
	// Palette stores HSB values in RGB channels (H=R, S=G, B=B), with alpha=1 for valid entries
	// Note: shader.eval() expects pixel coordinates, not normalized coordinates
	private static final String COLORIFY_MULTI_ALG2_SKSL = """
			uniform shader image;        // Grayscale input image
			uniform shader colorIndexes; // Region IDs encoded as RGB
			uniform shader palette;      // HSB palette (dynamic size), HSB stored in RGB channels
			uniform float paletteWidth;  // Width of palette texture for coordinate calculation

			// HSB to RGB conversion
			half3 hsb2rgb(half h, half s, half b) {
			    half c = b * s;
			    half hPrime = h * 6.0;
			    half x = c * (1.0 - abs(mod(hPrime, 2.0) - 1.0));
			    half3 rgb;
			    if (hPrime < 1.0) rgb = half3(c, x, 0.0);
			    else if (hPrime < 2.0) rgb = half3(x, c, 0.0);
			    else if (hPrime < 3.0) rgb = half3(0.0, c, x);
			    else if (hPrime < 4.0) rgb = half3(0.0, x, c);
			    else if (hPrime < 5.0) rgb = half3(x, 0.0, c);
			    else rgb = half3(c, 0.0, x);
			    half m = b - c;
			    return rgb + m;
			}

			half4 main(float2 coord) {
			    half gray = image.eval(coord).r;

			    // Decode region ID from colorIndexes
			    half4 indexColor = colorIndexes.eval(coord);
			    float regionId = floor(indexColor.r * 255.0 + 0.5) * 65536.0
			                   + floor(indexColor.g * 255.0 + 0.5) * 256.0
			                   + floor(indexColor.b * 255.0 + 0.5);

			    // Calculate 2D palette coordinates in pixel space
			    float py = floor(regionId / paletteWidth);
			    float px = regionId - py * paletteWidth;
			    float2 paletteCoord = float2(px + 0.5, py + 0.5);
			    half4 hsbColor = palette.eval(paletteCoord);

			    // If no color for this region, return gray
			    if (hsbColor.a < 0.01) {
			        return half4(gray, gray, gray, 1.0);
			    }

			    // HSB values are stored in RGB channels
			    half h = hsbColor.r;
			    half s = hsbColor.g;
			    half brightness = hsbColor.b;

			    // Algorithm 2: overlay formula
			    half I = brightness * 255.0;
			    half overlay = ((I / 255.0) * (I + (2.0 * gray) * (255.0 - I))) / 255.0;
			    half3 rgb = hsb2rgb(h, s, overlay);
			    return half4(rgb, 1.0);
			}
			""";

	// SkSL shader for colorifying with multiple colors (algorithm3)
	// Note: shader.eval() expects pixel coordinates, not normalized coordinates
	private static final String COLORIFY_MULTI_ALG3_SKSL = """
			uniform shader image;
			uniform shader colorIndexes;
			uniform shader palette;
			uniform float paletteWidth;  // Width of palette texture for coordinate calculation

			half3 hsb2rgb(half h, half s, half b) {
			    half c = b * s;
			    half hPrime = h * 6.0;
			    half x = c * (1.0 - abs(mod(hPrime, 2.0) - 1.0));
			    half3 rgb;
			    if (hPrime < 1.0) rgb = half3(c, x, 0.0);
			    else if (hPrime < 2.0) rgb = half3(x, c, 0.0);
			    else if (hPrime < 3.0) rgb = half3(0.0, c, x);
			    else if (hPrime < 4.0) rgb = half3(0.0, x, c);
			    else if (hPrime < 5.0) rgb = half3(x, 0.0, c);
			    else rgb = half3(c, 0.0, x);
			    half m = b - c;
			    return rgb + m;
			}

			half4 main(float2 coord) {
			    half gray = image.eval(coord).r;

			    half4 indexColor = colorIndexes.eval(coord);
			    float regionId = floor(indexColor.r * 255.0 + 0.5) * 65536.0
			                   + floor(indexColor.g * 255.0 + 0.5) * 256.0
			                   + floor(indexColor.b * 255.0 + 0.5);

			    float py = floor(regionId / paletteWidth);
			    float px = regionId - py * paletteWidth;
			    float2 paletteCoord = float2(px + 0.5, py + 0.5);
			    half4 hsbColor = palette.eval(paletteCoord);

			    if (hsbColor.a < 0.01) {
			        return half4(gray, gray, gray, 1.0);
			    }

			    half h = hsbColor.r;
			    half s = hsbColor.g;
			    half brightness = hsbColor.b;

			    // Algorithm 3: brightness scaling
			    half resultLevel;
			    if (brightness < 0.5) {
			        resultLevel = gray * (brightness * 2.0);
			    } else {
			        half range = (1.0 - brightness) * 2.0;
			        resultLevel = range * gray + (1.0 - range);
			    }
			    half3 rgb = hsb2rgb(h, s, resultLevel);
			    return half4(rgb, 1.0);
			}
			""";

	// SkSL shader for colorifying with multiple colors (solidColor - just uses the color directly)
	// Note: shader.eval() expects pixel coordinates, not normalized coordinates
	private static final String COLORIFY_MULTI_SOLID_SKSL = """
			uniform shader image;
			uniform shader colorIndexes;
			uniform shader palette;      // RGB palette for solid colors
			uniform float paletteWidth;  // Width of palette texture for coordinate calculation

			half4 main(float2 coord) {
			    half4 indexColor = colorIndexes.eval(coord);
			    float regionId = floor(indexColor.r * 255.0 + 0.5) * 65536.0
			                   + floor(indexColor.g * 255.0 + 0.5) * 256.0
			                   + floor(indexColor.b * 255.0 + 0.5);

			    float py = floor(regionId / paletteWidth);
			    float px = regionId - py * paletteWidth;
			    float2 paletteCoord = float2(px + 0.5, py + 0.5);
			    half4 regionColor = palette.eval(paletteCoord);

			    if (regionColor.a < 0.01) {
			        half gray = image.eval(coord).r;
			        return half4(gray, gray, gray, 1.0);
			    }

			    return half4(regionColor.rgb, 1.0);
			}
			""";

	private static RuntimeEffect colorifyMultiAlg2Effect;
	private static RuntimeEffect colorifyMultiAlg3Effect;
	private static RuntimeEffect colorifyMultiSolidEffect;

	private static RuntimeEffect getColorifyMultiAlg2Effect()
	{
		if (colorifyMultiAlg2Effect == null)
		{
			synchronized (effectLock)
			{
				if (colorifyMultiAlg2Effect == null)
				{
					colorifyMultiAlg2Effect = RuntimeEffect.Companion.makeForShader(COLORIFY_MULTI_ALG2_SKSL);
					if (colorifyMultiAlg2Effect == null)
					{
						throw new RuntimeException("Failed to compile COLORIFY_MULTI_ALG2_SKSL shader");
					}
				}
			}
		}
		return colorifyMultiAlg2Effect;
	}

	private static RuntimeEffect getColorifyMultiAlg3Effect()
	{
		if (colorifyMultiAlg3Effect == null)
		{
			synchronized (effectLock)
			{
				if (colorifyMultiAlg3Effect == null)
				{
					colorifyMultiAlg3Effect = RuntimeEffect.Companion.makeForShader(COLORIFY_MULTI_ALG3_SKSL);
					if (colorifyMultiAlg3Effect == null)
					{
						throw new RuntimeException("Failed to compile COLORIFY_MULTI_ALG3_SKSL shader");
					}
				}
			}
		}
		return colorifyMultiAlg3Effect;
	}

	private static RuntimeEffect getColorifyMultiSolidEffect()
	{
		if (colorifyMultiSolidEffect == null)
		{
			synchronized (effectLock)
			{
				if (colorifyMultiSolidEffect == null)
				{
					colorifyMultiSolidEffect = RuntimeEffect.Companion.makeForShader(COLORIFY_MULTI_SOLID_SKSL);
					if (colorifyMultiSolidEffect == null)
					{
						throw new RuntimeException("Failed to compile COLORIFY_MULTI_SOLID_SKSL shader");
					}
				}
			}
		}
		return colorifyMultiSolidEffect;
	}

	/**
	 * Colorifies a grayscale image using multiple colors based on region IDs. Each pixel's region ID is decoded from the colorIndexes
	 * image. The corresponding color is looked up and used to colorify the grayscale pixel.
	 *
	 * @param image
	 *            Grayscale source image
	 * @param colorMap
	 *            Map of region ID to color
	 * @param colorIndexes
	 *            RGB image encoding region IDs
	 * @param how
	 *            Colorify algorithm to use
	 * @return Colorified RGB image
	 */
	public static Image colorifyMulti(Image image, Map<Integer, Color> colorMap, Image colorIndexes, ColorifyAlgorithm how)
	{
		if (how == ColorifyAlgorithm.none)
		{
			return image;
		}

		SkiaImage skImage = (SkiaImage) image;
		SkiaImage skColorIndexes = (SkiaImage) colorIndexes;

		int width = colorIndexes.getWidth();
		int height = colorIndexes.getHeight();

		// Calculate optimal palette dimensions based on region IDs
		int[] paletteDims = calculatePaletteDimensions(colorMap);
		int paletteWidth = paletteDims[0];
		int paletteHeight = paletteDims[1];

		// Create palette texture - for algorithm2/3, store HSB; for solidColor, store RGB
		Image palette = (how == ColorifyAlgorithm.solidColor) ? createPaletteTexture(colorMap, paletteWidth, paletteHeight) : createHSBPaletteTexture(colorMap, paletteWidth, paletteHeight);
		SkiaImage skPalette = (SkiaImage) palette;

		try
		{
			if (isGPUAccelerated() && canUseGPUForSize(width, height))
			{
				return GPUExecutor.getInstance().submit(() -> colorifyMultiImpl(skImage, skColorIndexes, skPalette, width, height, paletteWidth, how, true));
			}
			else
			{
				return colorifyMultiImpl(skImage, skColorIndexes, skPalette, width, height, paletteWidth, how, false);
			}
		}
		finally
		{
			palette.close();
		}
	}

	/**
	 * Creates a 2D palette texture storing HSB values. Region ID r is stored at pixel position (r % width, r / width). HSB values are stored
	 * in RGB channels (H=R, S=G, B=B).
	 *
	 * @param colors
	 *            Map of region ID to color
	 * @param width
	 *            Palette width (used for coordinate calculation)
	 * @param height
	 *            Palette height
	 * @return The created palette image
	 */
	private static Image createHSBPaletteTexture(Map<Integer, Color> colors, int width, int height)
	{
		Image palette = Image.create(width, height, ImageType.ARGB);
		try (PixelWriter writer = palette.createPixelWriter())
		{
			// Initialize with transparent (alpha=0) to indicate missing regions
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					writer.setRGB(x, y, 0, 0, 0, 0);
				}
			}

			// Fill in the HSB values from the map
			int maxSlots = width * height;
			for (Map.Entry<Integer, Color> entry : colors.entrySet())
			{
				int regionId = entry.getKey();
				if (regionId < 0 || regionId >= maxSlots)
				{
					continue;
				}
				Color color = entry.getValue();
				float[] hsb = color.getHSB();
				int px = regionId % width;
				int py = regionId / width;
				// Store HSB as RGB (0-255 range)
				writer.setRGB(px, py, (int) (hsb[0] * 255), (int) (hsb[1] * 255), (int) (hsb[2] * 255), 255);
			}
		}
		return palette;
	}

	private static Image colorifyMultiImpl(SkiaImage skImage, SkiaImage skColorIndexes, SkiaImage skPalette, int width, int height, int paletteWidth, ColorifyAlgorithm how, boolean useGPU)
	{
		org.jetbrains.skia.Image skiImage = skImage.getSkiaImage();
		org.jetbrains.skia.Image skiColorIndexes = skColorIndexes.getSkiaImage();
		org.jetbrains.skia.Image skiPalette = skPalette.getSkiaImage();

		Matrix33 identity = Matrix33.Companion.getIDENTITY();

		Shader imageShader = skiImage.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);
		Shader colorIndexesShader = skiColorIndexes.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);
		Shader paletteShader = skiPalette.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);

		RuntimeEffect effect;
		switch (how)
		{
			case algorithm2:
				effect = getColorifyMultiAlg2Effect();
				break;
			case algorithm3:
				effect = getColorifyMultiAlg3Effect();
				break;
			case solidColor:
				effect = getColorifyMultiSolidEffect();
				break;
			default:
				throw new IllegalArgumentException("Unsupported colorify algorithm: " + how);
		}

		RuntimeShaderBuilder builder = new RuntimeShaderBuilder(effect);
		builder.child("image", imageShader);
		builder.child("colorIndexes", colorIndexesShader);
		builder.child("palette", paletteShader);
		builder.uniform("paletteWidth", (float) paletteWidth);
		Shader resultShader = builder.makeShader(identity);

		Surface surface = useGPU ? createGPUSurfaceOnGPUThread(width, height) : createCPUSurface(width, height);
		Canvas canvas = surface.getCanvas();

		Paint paint = new Paint();
		paint.setShader(resultShader);
		canvas.drawRect(Rect.makeWH(width, height), paint);

		ImageFromSurface result = createImageFromSurface(surface, width, height, ImageType.RGB, useGPU);

		// Clean up
		if (!result.surfaceTransferred())
		{
			surface.close();
		}
		paint.close();
		resultShader.close();
		imageShader.close();
		colorIndexesShader.close();
		paletteShader.close();
		builder.close();

		return result.image();
	}

	// ==================== copyAlphaTo ====================

	// SkSL shader for copying RGB from target and alpha from alphaSource
	private static final String COPY_ALPHA_TO_SKSL = """
			uniform shader target;
			uniform shader alphaSource;

			half4 main(float2 coord) {
			    half4 targetColor = target.eval(coord);
			    half4 sourceColor = alphaSource.eval(coord);
			    return half4(targetColor.rgb, sourceColor.a);
			}
			""";

	private static RuntimeEffect copyAlphaToEffect;

	private static RuntimeEffect getCopyAlphaToEffect()
	{
		if (copyAlphaToEffect == null)
		{
			synchronized (effectLock)
			{
				if (copyAlphaToEffect == null)
				{
					copyAlphaToEffect = RuntimeEffect.Companion.makeForShader(COPY_ALPHA_TO_SKSL);
					if (copyAlphaToEffect == null)
					{
						throw new RuntimeException("Failed to compile COPY_ALPHA_TO_SKSL shader");
					}
				}
			}
		}
		return copyAlphaToEffect;
	}

	/**
	 * Copies RGB from target and alpha from alphaSource into a new ARGB image.
	 */
	public static Image copyAlphaTo(Image target, Image alphaSource)
	{
		SkiaImage skTarget = (SkiaImage) target;
		SkiaImage skAlphaSource = (SkiaImage) alphaSource;

		int width = target.getWidth();
		int height = target.getHeight();

		if (isGPUAccelerated() && canUseGPUForSize(width, height))
		{
			return GPUExecutor.getInstance().submit(() -> copyAlphaToImpl(skTarget, skAlphaSource, width, height, true));
		}
		else
		{
			return copyAlphaToImpl(skTarget, skAlphaSource, width, height, false);
		}
	}

	private static Image copyAlphaToImpl(SkiaImage skTarget, SkiaImage skAlphaSource, int width, int height, boolean useGPU)
	{
		org.jetbrains.skia.Image skiTarget = skTarget.getSkiaImage();
		org.jetbrains.skia.Image skiAlphaSource = skAlphaSource.getSkiaImage();

		Matrix33 identity = Matrix33.Companion.getIDENTITY();

		Shader targetShader = skiTarget.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);
		Shader alphaSourceShader = skiAlphaSource.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);

		RuntimeEffect effect = getCopyAlphaToEffect();
		RuntimeShaderBuilder builder = new RuntimeShaderBuilder(effect);
		builder.child("target", targetShader);
		builder.child("alphaSource", alphaSourceShader);
		Shader resultShader = builder.makeShader(identity);

		Surface surface = useGPU ? createGPUSurfaceOnGPUThread(width, height) : createCPUSurface(width, height);
		Canvas canvas = surface.getCanvas();

		Paint paint = new Paint();
		paint.setShader(resultShader);
		canvas.drawRect(Rect.makeWH(width, height), paint);

		ImageFromSurface result = createImageFromSurface(surface, width, height, ImageType.ARGB, useGPU);

		// Clean up
		if (!result.surfaceTransferred())
		{
			surface.close();
		}
		paint.close();
		resultShader.close();
		targetShader.close();
		alphaSourceShader.close();
		builder.close();

		return result.image();
	}

	// ==================== In-Place Operations ====================

	/**
	 * In-place version of maskWithColor that modifies the source image directly. This avoids allocating a new image and can improve
	 * performance.
	 */
	public static void maskWithColorInPlace(Image image, Color color, Image mask, boolean invertMask)
	{
		SkiaImage skImage = (SkiaImage) image;
		SkiaImage skMask = (SkiaImage) mask;

		int width = image.getWidth();
		int height = image.getHeight();

		// Extract color components before lambda
		float r = color.getRed() / 255f;
		float g = color.getGreen() / 255f;
		float b = color.getBlue() / 255f;
		float a = color.getAlpha() / 255f;

		if (isGPUAccelerated() && canUseGPUForSize(width, height))
		{
			GPUExecutor.getInstance().submit(() ->
			{
				maskWithColorInPlaceImpl(skImage, skMask, width, height, r, g, b, a, invertMask, true);
				return null;
			});
		}
		else
		{
			maskWithColorInPlaceImpl(skImage, skMask, width, height, r, g, b, a, invertMask, false);
		}
	}

	private static void maskWithColorInPlaceImpl(SkiaImage skImage, SkiaImage skMask, int width, int height, float r, float g, float b, float a, boolean invertMask, boolean useGPU)
	{
		org.jetbrains.skia.Image skiImage = skImage.getSkiaImage();
		org.jetbrains.skia.Image skiMask = skMask.getSkiaImage();

		Matrix33 identity = Matrix33.Companion.getIDENTITY();

		Shader imageShader = skiImage.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);
		Shader maskShader = skiMask.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);

		RuntimeEffect effect = getMaskWithColorEffect();
		RuntimeShaderBuilder builder = new RuntimeShaderBuilder(effect);
		builder.child("image", imageShader);
		builder.child("mask", maskShader);
		builder.uniform("colorRGB", r, g, b);
		builder.uniform("colorAlpha", a);
		builder.uniform("invertMask", invertMask ? 1f : 0f);
		Shader resultShader = builder.makeShader(identity);

		Surface surface = useGPU ? createGPUSurfaceOnGPUThread(width, height) : createCPUSurface(width, height);
		Canvas canvas = surface.getCanvas();

		Paint paint = new Paint();
		paint.setShader(resultShader);
		canvas.drawRect(Rect.makeWH(width, height), paint);

		// Replace the original image's pixels with the result (stays on GPU when possible)
		skImage.replaceFromSurface(surface, useGPU);

		// Clean up
		surface.close();
		paint.close();
		resultShader.close();
		imageShader.close();
		maskShader.close();
		builder.close();
	}

	// ==================== maskWithColorInRegion ====================

	// SkSL shader for blending image with solid color using a mask with offset support.
	// Similar to MASK_WITH_COLOR_SKSL but allows the mask to be offset relative to the image.
	// When the mask coordinate falls outside the mask bounds, the image pixel is unchanged.
	private static final String MASK_WITH_COLOR_IN_REGION_SKSL = """
			uniform shader image;
			uniform shader mask;
			uniform half3 colorRGB;
			uniform half colorAlpha;
			uniform half invertMask;
			uniform float2 maskOffset;
			uniform float2 maskSize;

			half4 main(float2 coord) {
			    half4 imgColor = image.eval(coord);  // premultiplied

			    // Calculate mask coordinate with offset
			    float2 maskCoord = coord + maskOffset;

			    // Bounds check - if out of bounds, return image unchanged
			    if (maskCoord.x < 0.0 || maskCoord.y < 0.0 ||
			        maskCoord.x >= maskSize.x || maskCoord.y >= maskSize.y) {
			        return imgColor;
			    }

			    half m = mask.eval(maskCoord).r;

			    // Calculate blend factor matching Java logic:
			    // maskLevel = m * colorAlpha (in 0-1 range here)
			    // overlayAlpha = 1 - maskLevel (non-inverted) or maskLevel (inverted)
			    // blendFactor = overlayAlpha (how much of color to show)
			    half blendFactor;
			    if (invertMask > 0.5) {
			        blendFactor = m * colorAlpha;
			    } else {
			        blendFactor = 1.0 - m * colorAlpha;
			    }

			    // Unpremultiply image color to get straight RGB for blending
			    half3 imgRGB = imgColor.a > 0.0 ? imgColor.rgb / imgColor.a : half3(0.0);

			    // Blend in straight alpha space
			    half3 resultRGB = mix(imgRGB, colorRGB, blendFactor);
			    half resultA = imgColor.a;

			    // Output premultiplied for GPU surface
			    return half4(resultRGB * resultA, resultA);
			}
			""";

	private static RuntimeEffect maskWithColorInRegionEffect;

	private static RuntimeEffect getMaskWithColorInRegionEffect()
	{
		if (maskWithColorInRegionEffect == null)
		{
			synchronized (effectLock)
			{
				if (maskWithColorInRegionEffect == null)
				{
					maskWithColorInRegionEffect = RuntimeEffect.Companion.makeForShader(MASK_WITH_COLOR_IN_REGION_SKSL);
					if (maskWithColorInRegionEffect == null)
					{
						throw new RuntimeException("Failed to compile MASK_WITH_COLOR_IN_REGION_SKSL shader");
					}
				}
			}
		}
		return maskWithColorInRegionEffect;
	}

	/**
	 * In-place version of maskWithColorInRegion that modifies the source image directly. The mask can be larger than the image, with
	 * imageOffsetInMask specifying where the image's (0,0) corresponds to in the mask coordinate space.
	 */
	public static void maskWithColorInRegionInPlace(Image image, Color color, Image mask, boolean invertMask, int offsetX, int offsetY)
	{
		SkiaImage skImage = (SkiaImage) image;
		SkiaImage skMask = (SkiaImage) mask;

		int width = image.getWidth();
		int height = image.getHeight();

		float r = color.getRed() / 255f;
		float g = color.getGreen() / 255f;
		float b = color.getBlue() / 255f;
		float a = color.getAlpha() / 255f;

		if (isGPUAccelerated() && canUseGPUForSize(width, height))
		{
			GPUExecutor.getInstance().submit(() ->
			{
				maskWithColorInRegionInPlaceImpl(skImage, skMask, width, height, r, g, b, a, invertMask, offsetX, offsetY, mask.getWidth(), mask.getHeight(), true);
				return null;
			});
		}
		else
		{
			maskWithColorInRegionInPlaceImpl(skImage, skMask, width, height, r, g, b, a, invertMask, offsetX, offsetY, mask.getWidth(), mask.getHeight(), false);
		}
	}

	private static void maskWithColorInRegionInPlaceImpl(SkiaImage skImage, SkiaImage skMask, int width, int height, float r, float g, float b, float a, boolean invertMask, int offsetX, int offsetY,
			int maskWidth, int maskHeight, boolean useGPU)
	{
		org.jetbrains.skia.Image skiImage = skImage.getSkiaImage();
		org.jetbrains.skia.Image skiMask = skMask.getSkiaImage();

		Matrix33 identity = Matrix33.Companion.getIDENTITY();

		Shader imageShader = skiImage.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);
		Shader maskShader = skiMask.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);

		RuntimeEffect effect = getMaskWithColorInRegionEffect();
		RuntimeShaderBuilder builder = new RuntimeShaderBuilder(effect);
		builder.child("image", imageShader);
		builder.child("mask", maskShader);
		builder.uniform("colorRGB", r, g, b);
		builder.uniform("colorAlpha", a);
		builder.uniform("invertMask", invertMask ? 1f : 0f);
		builder.uniform("maskOffset", (float) offsetX, (float) offsetY);
		builder.uniform("maskSize", (float) maskWidth, (float) maskHeight);
		Shader resultShader = builder.makeShader(identity);

		Surface surface = useGPU ? createGPUSurfaceOnGPUThread(width, height) : createCPUSurface(width, height);
		Canvas canvas = surface.getCanvas();

		Paint paint = new Paint();
		paint.setShader(resultShader);
		canvas.drawRect(Rect.makeWH(width, height), paint);

		// Replace the original image's pixels with the result
		skImage.replaceFromSurface(surface, useGPU);

		// Clean up
		surface.close();
		paint.close();
		resultShader.close();
		imageShader.close();
		maskShader.close();
		builder.close();
	}

	/**
	 * In-place version of maskWithImage that modifies image1 directly. This avoids allocating a new image and can improve performance.
	 */
	public static void maskWithImageInPlace(Image image1, Image image2, Image mask)
	{
		SkiaImage skImage1 = (SkiaImage) image1;
		SkiaImage skImage2 = (SkiaImage) image2;
		SkiaImage skMask = (SkiaImage) mask;

		int width = image1.getWidth();
		int height = image1.getHeight();

		if (isGPUAccelerated() && canUseGPUForSize(width, height))
		{
			GPUExecutor.getInstance().submit(() ->
			{
				maskWithImageInPlaceImpl(skImage1, skImage2, skMask, width, height, true);
				return null;
			});
		}
		else
		{
			maskWithImageInPlaceImpl(skImage1, skImage2, skMask, width, height, false);
		}
	}

	private static void maskWithImageInPlaceImpl(SkiaImage skImage1, SkiaImage skImage2, SkiaImage skMask, int width, int height, boolean useGPU)
	{
		org.jetbrains.skia.Image skiImage1 = skImage1.getSkiaImage();
		org.jetbrains.skia.Image skiImage2 = skImage2.getSkiaImage();
		org.jetbrains.skia.Image skiMask = skMask.getSkiaImage();

		Matrix33 identity = Matrix33.Companion.getIDENTITY();

		Shader shader1 = skiImage1.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);
		Shader shader2 = skiImage2.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);
		Shader shaderMask = skiMask.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, identity);

		RuntimeEffect effect = getMaskWithImageEffect();
		RuntimeShaderBuilder builder = new RuntimeShaderBuilder(effect);
		builder.child("image1", shader1);
		builder.child("image2", shader2);
		builder.child("mask", shaderMask);
		Shader resultShader = builder.makeShader(identity);

		Surface surface = useGPU ? createGPUSurfaceOnGPUThread(width, height) : createCPUSurface(width, height);
		Canvas canvas = surface.getCanvas();

		Paint paint = new Paint();
		paint.setShader(resultShader);
		canvas.drawRect(Rect.makeWH(width, height), paint);

		// Replace the original image's pixels with the result (stays on GPU when possible)
		skImage1.replaceFromSurface(surface, useGPU);

		// Clean up
		surface.close();
		paint.close();
		resultShader.close();
		shader1.close();
		shader2.close();
		shaderMask.close();
		builder.close();
	}

	// ==================== Checks for whether to use GPU and shaders ====================

	/**
	 * Checks if GPU acceleration is being used for shader operations.
	 */
	public static boolean isGPUAccelerated()
	{
		return GPUExecutor.getInstance().isGPUAvailable();
	}

	/**
	 * Determines whether the given images should run on the GPU. Returns true if GPU is available, all images are SkiaImages, and at least
	 * one image is GPU-backed. Small images that aren't GPU-backed will be uploaded temporarily during shader execution.
	 */
	public static boolean shouldRunOnGPU(Image... images)
	{
		return isGPUAccelerated() && areAllSkiaImagesAndAtLeastOneGpuBacked(images);
	}

	/**
	 * Returns true if Skia shaders should be used for the given images. This is true when shaders are enabled and all images are
	 * SkiaImages. Shaders will run on GPU if available, otherwise on CPU using Skia's optimized CPU rasterizer.
	 */
	public static boolean shouldUseSkiaShaders(Image... images)
	{
		return GPUExecutor.getInstance().isShadersEnabled() && areAllSKiaImages(images);
	}

	public static boolean areAllSKiaImages(Image... images)
	{
		for (Image image : images)
		{
			if (!(image instanceof SkiaImage))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks if all images are SkiaImage instances and at least one has GPU enabled. This allows shader operations to run on GPU even when
	 * some images (like small masks) are below the GPU threshold - they will be uploaded temporarily.
	 */
	private static boolean areAllSkiaImagesAndAtLeastOneGpuBacked(Image... images)
	{
		boolean hasGpuBacked = false;
		for (Image image : images)
		{
			if (!(image instanceof SkiaImage))
			{
				return false;
			}
			if (((SkiaImage) image).isGpuEnabled())
			{
				hasGpuBacked = true;
			}
		}
		return hasGpuBacked;
	}
}
