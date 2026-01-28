package nortantis.platform;

public interface PixelReader extends AutoCloseable
{
	public int getGrayLevel(int x, int y);

	public int getBandLevel(int x, int y, int band);

	public int getRGB(int x, int y);

	public Color getPixelColor(int x, int y);

	public float getNormalizedPixelLevel(int x, int y);

	public int getAlpha(int x, int y);

	@Override
	void close();
}
