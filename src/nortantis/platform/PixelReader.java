package nortantis.platform;

import nortantis.geom.IntRectangle;

public interface PixelReader extends AutoCloseable
{
	/**
	 * Returns the bounds this reader was created with, or null if reading the entire image.
	 */
	public IntRectangle getBounds();

	public int getGrayLevel(int x, int y);

	public int getBandLevel(int x, int y, int band);

	public int getRGB(int x, int y);

	public Color getPixelColor(int x, int y);

	public float getNormalizedPixelLevel(int x, int y);

	public int getAlpha(int x, int y);

	@Override
	void close();
}
