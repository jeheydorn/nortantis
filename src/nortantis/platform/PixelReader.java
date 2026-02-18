package nortantis.platform;

import nortantis.geom.IntRectangle;

public interface PixelReader extends AutoCloseable
{
	/**
	 * Refreshes the cached pixel data for the given region by re-reading from the source image. For implementations that maintain a live
	 * view of the image data (like AWT), this is a no-op.
	 */
	void refreshRegion(IntRectangle bounds);

	public int getGrayLevel(int x, int y);

	public int getBandLevel(int x, int y, int band);

	public int getRGB(int x, int y);

	public Color getPixelColor(int x, int y);

	public float getNormalizedPixelLevel(int x, int y);

	public int getAlpha(int x, int y);

	@Override
	void close();
}
