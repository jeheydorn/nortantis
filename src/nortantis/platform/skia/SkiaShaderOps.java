package nortantis.platform.skia;

import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.util.ImageHelper.ColorifyAlgorithm;
import nortantis.util.Logger;
import org.jetbrains.skia.*;

/**
 * Provides high-performance image operations using Skia shaders and blend modes.
 * These operations use GPU acceleration when available via GPUExecutor,
 * falling back to SIMD-optimized CPU rendering otherwise.
 */
public class SkiaShaderOps
{
	private static final Object effectLock = new Object();

	// ==================== Surface Creation Helper ====================

	/**
	 * Creates a CPU raster surface for shader operations.
	 */
	private static Surface createCPUSurface(int width, int height)
	{
		return Surface.Companion.makeRaster(new ImageInfo(width, height, ColorType.Companion.getN32(), ColorAlphaType.UNPREMUL, null));
	}

	/**
	 * Creates a GPU surface. Must be called from the GPU thread.
	 * GPU render targets require PREMUL alpha type.
	 */
	private static Surface createGPUSurfaceOnGPUThread(int width, int height)
	{
		DirectContext ctx = GPUExecutor.getInstance().getContext();
		if (ctx == null)
		{
			throw new IllegalStateException("DirectContext is null on GPU thread");
		}
		return Surface.Companion.makeRenderTarget(
			ctx,
			false,
			new ImageInfo(width, height, ColorType.Companion.getN32(), ColorAlphaType.PREMUL)
		);
	}

	/**
	 * Reads pixels from a surface into a new Bitmap.
	 */
	private static Bitmap readPixelsToBitmap(Surface surface, int width, int height)
	{
		org.jetbrains.skia.Image resultSkiaImage = surface.makeImageSnapshot();
		Bitmap resultBitmap = new Bitmap();
		resultBitmap.allocPixels(new ImageInfo(width, height, ColorType.Companion.getN32(), ColorAlphaType.UNPREMUL, null));
		resultSkiaImage.readPixels(resultBitmap, 0, 0);
		resultSkiaImage.close();
		return resultBitmap;
	}

	// ==================== maskWithImage ====================

	// SkSL shader for mask blending: result = mix(image2, image1, mask)
	private static final String MASK_WITH_IMAGE_SKSL = """
			uniform shader image1;
			uniform shader image2;
			uniform shader mask;

			half4 main(float2 coord) {
			    half4 c1 = image1.eval(coord);
			    half4 c2 = image2.eval(coord);
			    half m = mask.eval(coord).r;
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
	 * Blends two images using a grayscale mask.
	 * result = mask * image1 + (1 - mask) * image2
	 */
	public static Image maskWithImage(Image image1, Image image2, Image mask)
	{
		SkiaImage skImage1 = (SkiaImage) image1;
		SkiaImage skImage2 = (SkiaImage) image2;
		SkiaImage skMask = (SkiaImage) mask;

		int width = image1.getWidth();
		int height = image1.getHeight();
		ImageType resultType = (image1.hasAlpha() || image2.hasAlpha()) ? ImageType.ARGB : ImageType.RGB;

		// Check if we should use GPU
		if (GPUExecutor.getInstance().isGPUAvailable())
		{
			return GPUExecutor.getInstance().submit(() ->
				maskWithImageImpl(skImage1, skImage2, skMask, width, height, resultType, true));
		}
		else
		{
			return maskWithImageImpl(skImage1, skImage2, skMask, width, height, resultType, false);
		}
	}

	private static Image maskWithImageImpl(SkiaImage skImage1, SkiaImage skImage2, SkiaImage skMask,
			int width, int height, ImageType resultType, boolean useGPU)
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

		Bitmap resultBitmap = readPixelsToBitmap(surface, width, height);

		// Clean up
		surface.close();
		paint.close();
		resultShader.close();
		shader1.close();
		shader2.close();
		shaderMask.close();
		builder.close();

		return new SkiaImage(resultBitmap, resultType);
	}

	// ==================== maskWithColor ====================

	// SkSL shader for blending image with solid color using mask.
	// Matches the logic in ImageHelper.maskWithColorInRegion:
	// - Creates an overlay with (color.rgb, overlayAlpha) where overlayAlpha = 255 - maskLevel
	// - maskLevel = mask * colorAlpha (or inverted: 255 - mask * colorAlpha)
	// - Composites overlay onto image using standard SRC_OVER blending
	// The result is: result = mix(image, color, blendFactor)
	// where blendFactor = (1 - mask * colorAlpha) for non-inverted
	// and blendFactor = (mask * colorAlpha) for inverted
	private static final String MASK_WITH_COLOR_SKSL = """
			uniform shader image;
			uniform shader mask;
			uniform half3 colorRGB;
			uniform half colorAlpha;
			uniform half invertMask;

			half4 main(float2 coord) {
			    half4 imgColor = image.eval(coord);
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

			    // Blend image with color
			    half3 resultRGB = mix(imgColor.rgb, colorRGB, blendFactor);
			    return half4(resultRGB, imgColor.a);
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
				}
			}
		}
		return maskWithColorEffect;
	}

	/**
	 * Blends an image with a solid color using a grayscale mask.
	 * Matches the behavior of ImageHelper.maskWithColorInRegion:
	 * - mask=0 (black), not inverted -> fully show color
	 * - mask=1 (white), not inverted -> fully show image
	 * - The color's alpha modulates the mask effect
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

		// Check if we should use GPU
		if (GPUExecutor.getInstance().isGPUAvailable())
		{
			return GPUExecutor.getInstance().submit(() ->
				maskWithColorImpl(skImage, skMask, width, height, r, g, b, a, invertMask, resultType, true));
		}
		else
		{
			return maskWithColorImpl(skImage, skMask, width, height, r, g, b, a, invertMask, resultType, false);
		}
	}

	private static Image maskWithColorImpl(SkiaImage skImage, SkiaImage skMask, int width, int height,
			float r, float g, float b, float a, boolean invertMask, ImageType resultType, boolean useGPU)
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

		Bitmap resultBitmap = readPixelsToBitmap(surface, width, height);

		// Clean up
		surface.close();
		paint.close();
		resultShader.close();
		imageShader.close();
		maskShader.close();
		builder.close();

		return new SkiaImage(resultBitmap, resultType);
	}

	// ==================== setAlphaFromMask ====================

	// SkSL shader to set alpha from grayscale mask
	// Since we use UNPREMUL surfaces, RGB stays unchanged, only alpha changes
	private static final String SET_ALPHA_FROM_MASK_SKSL = """
			uniform shader image;
			uniform shader mask;
			uniform half invertMask;

			half4 main(float2 coord) {
			    half4 c = image.eval(coord);
			    half m = mask.eval(coord).r;
			    if (invertMask > 0.5) {
			        m = 1.0 - m;
			    }
			    // Take minimum of mask and existing alpha
			    half newAlpha = min(m, c.a);
			    // RGB stays unchanged (UNPREMUL format)
			    return half4(c.rgb, newAlpha);
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
	 * Sets alpha channel from a grayscale mask.
	 * The result alpha is min(mask, originalAlpha).
	 */
	public static Image setAlphaFromMask(Image image, Image alphaMask, boolean invertMask)
	{
		SkiaImage skImage = (SkiaImage) image;
		SkiaImage skMask = (SkiaImage) alphaMask;

		int width = image.getWidth();
		int height = image.getHeight();

		// Check if we should use GPU
		if (GPUExecutor.getInstance().isGPUAvailable())
		{
			return GPUExecutor.getInstance().submit(() ->
				setAlphaFromMaskImpl(skImage, skMask, width, height, invertMask, true));
		}
		else
		{
			return setAlphaFromMaskImpl(skImage, skMask, width, height, invertMask, false);
		}
	}

	private static Image setAlphaFromMaskImpl(SkiaImage skImage, SkiaImage skMask, int width, int height,
			boolean invertMask, boolean useGPU)
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

		Bitmap resultBitmap = readPixelsToBitmap(surface, width, height);

		// Clean up
		surface.close();
		paint.close();
		resultShader.close();
		imageShader.close();
		maskShader.close();
		builder.close();

		return new SkiaImage(resultBitmap, ImageType.ARGB);
	}

	// ==================== colorify ====================

	// SkSL shader for colorify algorithm2
	// Takes grayscale image and applies HSB color transformation
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
			    half gray = image.eval(coord).r;
			    half I = hsb.z * 255.0;
			    half overlay = ((I / 255.0) * (I + (2.0 * gray) * (255.0 - I))) / 255.0;
			    half3 rgb = hsb2rgb(hsb.x, hsb.y, overlay);
			    return half4(rgb, alpha);
			}
			""";

	// SkSL shader for colorify algorithm3
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
			    return half4(rgb, alpha);
			}
			""";

	// SkSL shader for colorify solidColor (just outputs the color)
	private static final String COLORIFY_SOLID_SKSL = """
			uniform half4 color;

			half4 main(float2 coord) {
			    return color;
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

		// Check if we should use GPU
		if (GPUExecutor.getInstance().isGPUAvailable())
		{
			return GPUExecutor.getInstance().submit(() ->
				colorifyImpl(skImage, width, height, how, hsb, alpha, r, g, b, resultType, true));
		}
		else
		{
			return colorifyImpl(skImage, width, height, how, hsb, alpha, r, g, b, resultType, false);
		}
	}

	private static Image colorifyImpl(SkiaImage skImage, int width, int height, ColorifyAlgorithm how,
			float[] hsb, float alpha, float r, float g, float b, ImageType resultType, boolean useGPU)
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

		Bitmap resultBitmap = readPixelsToBitmap(surface, width, height);

		// Clean up
		surface.close();
		paint.close();
		resultShader.close();
		imageShader.close();
		builder.close();

		return new SkiaImage(resultBitmap, resultType);
	}

	/**
	 * Checks if GPU acceleration is being used for shader operations.
	 */
	public static boolean isGPUAccelerated()
	{
		return GPUExecutor.getInstance().isGPUAvailable();
	}

	public boolean canProcessOnGPU(Image... images)
	{
		return areAllSkiaImages(images) && isGPUAccelerated();
	}


	/**
	 * Checks if all images are SkiaImage instances.
	 */
	public static boolean areAllSkiaImages(Image... images)
	{
		for (Image img : images)
		{
			if (!(img instanceof SkiaImage))
			{
				return false;
			}
		}
		return true;
	}
}
