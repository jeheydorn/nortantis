package nortantis.platform;

public abstract class Color
{
	public abstract int getRGB();

	public abstract int getRed();
	public abstract int getGreen();
	public abstract int getBlue();
	public abstract int getAlpha();
	
	public boolean hasTransparency()
	{
		return getAlpha() < Image.getMaxPixelLevelForType(ImageType.ARGB);
	}

	public static Color create(int rgb)
	{
		return create(rgb, false);
	}

	public static Color create(int rgb, boolean hasAlpha)
	{
		return PlatformFactory.getInstance().createColor(rgb, hasAlpha);
	}

	public static Color create(int red, int green, int blue)
	{
		return PlatformFactory.getInstance().createColor(red, green, blue);
	}

	public static Color create(int red, int green, int blue, int alpha)
	{
		return PlatformFactory.getInstance().createColor(red, green, blue, alpha);
	}

	/**
	 * Create a color from floating point values for red, green, and blue, ranging from 0.0 - 1.0.
	 */
	public static Color create(float red, float green, float blue)
	{
		return PlatformFactory.getInstance().createColor(red, green, blue);
	}

	public final static Color black = create(0, 0, 0);
	public final static Color white = create(255, 255, 255);
	public final static Color green = create(0, 255, 0);
	public final static Color yellow = create(255, 255, 0);
	public final static Color lightGray = create(192, 192, 192);
	public final static Color gray = create(128, 128, 128);
	public final static Color darkGray = create(64, 64, 64);
	public final static Color red = create(255, 0, 0);
	public final static Color pink = create(255, 175, 175);
	public final static Color orange = create(255, 200, 0);
	public final static Color magenta = create(255, 0, 255);
	public final static Color cyan = create(0, 255, 255);
	public final static Color blue = create(0, 0, 255);

	/**
	 * Converts this color to HSB
	 * 
	 * @param color
	 * @return An array with 3 elements: result[0] = hue result[1] = saturation result[3] = value
	 * 
	 */
	public abstract float[] getHSB();

	/**
	 * Creates a new color object from the given hue, saturation, and brightness.
	 * 
	 * @return A new color object.
	 */
	public static Color createFromHSB(float hue, float saturation, float brightness)
	{
		return PlatformFactory.getInstance().createColorFromHSB(hue, saturation, brightness);
	}
}
