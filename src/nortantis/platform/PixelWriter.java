package nortantis.platform;

/**
 * Write-only interface for pixel access. Use this when you need to write pixels to an image without needing to read the existing values
 * first.
 *
 * For Skia images, createPixelWriter() allocates an empty array without reading pixels from the image, making it more efficient for pure
 * write operations like generating noise.
 */
public interface PixelWriter extends AutoCloseable
{
	void setGrayLevel(int x, int y, int level);

	void setBandLevel(int x, int y, int band, int level);

	void setPixelColor(int x, int y, Color color);

	void setRGB(int x, int y, int rgb);

	void setRGB(int x, int y, int red, int green, int blue);

	void setRGB(int x, int y, int red, int green, int blue, int alpha);

	@Override
	void close();
}
