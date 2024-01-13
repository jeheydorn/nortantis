package nortantis.util.platform.awt;

import java.awt.Graphics2D;

import nortantis.util.platform.Image;
import nortantis.util.platform.Painter;

public class AwtPainter extends Painter
{
	public Graphics2D g;
	
	public AwtPainter(Graphics2D graphics)
	{
		this.g = graphics;
	}

	@Override
	public void drawImage(Image image, int x, int y)
	{
		g.drawImage(((AwtImage)image).image, x, y, null);
	}

	@Override
	public void dispose()
	{
		g.dispose();
	}
}
