package nortantis.platform;

import nortantis.platform.awt.AwtPixelReader;

public interface PixelReaderWriter extends PixelReader, AutoCloseable
{
	public void setGrayLevel(int x, int y, int level);

	public void setBandLevel(int x, int y, int band, int level);

	public int getAlpha(int x, int y);

	public void setPixelColor(int x, int y, Color color);

	public void setRGB(int x, int y, int rgb);

	public void setRGB(int x, int y, int red, int green, int blue);

	public void setRGB(int x, int y, int red, int green, int blue, int alpha);

	public void setAlpha(int x, int y, int alpha);

}
