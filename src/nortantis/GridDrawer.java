package nortantis;

import java.util.ArrayList;
import java.util.List;

import nortantis.geom.FloatPoint;
import nortantis.platform.Color;
import nortantis.platform.DrawQuality;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.util.ImageHelper;

// TODO Convert to wrapper classes
public class GridDrawer
{
	public static void drawGrid(Image image, GridOverlayShape type, int alpha, int columns, GridOverlayOffset xOffset,
			GridOverlayOffset yOffset, double resolutionScale)
	{
		Image hexImage = Image.create(image.getWidth(), image.getHeight(), ImageType.ARGB);
		ImageHelper.setAlphaOfAllPixels(hexImage, 0);

		{
			Painter p = hexImage.createPainter(DrawQuality.High);
			p.setColor(Color.black);
			p.setBasicStroke(10f * (float) resolutionScale); // TODO make the width a parameter

			int width = image.getWidth();
			int height = image.getHeight();

			float cellWidth = (float) width / columns;

			float xOffsetToUse = cellWidth * xOffset.getScale();
			float yOffsetToUse = cellWidth * yOffset.getScale();

			switch (type)
			{
			case Squares -> drawSquareGrid(p, width, height, cellWidth, xOffsetToUse, yOffsetToUse);
			case Vertical_hexes -> drawVerticalHexGrid(p, width, height, cellWidth, xOffsetToUse, yOffsetToUse);
			case Horizontal_hexes -> drawHorizontalHexGrid(p, width, height, cellWidth, xOffsetToUse, yOffsetToUse);
			}

			p.dispose();
		}

		{
			Painter p = image.createPainter();
			p.drawImage(ImageHelper.applyAlpha(hexImage, alpha), 0, 0);
			p.dispose();
		}

	}

	private static void drawSquareGrid(Painter p, int width, int height, float cellWidth, float xOffset, float yOffset)
	{
		for (float x = xOffset; x < width; x += cellWidth)
		{
			p.drawLine(x, 0, x, height);
		}
		for (float y = yOffset; y < height; y += cellWidth)
		{
			p.drawLine(0, y, width, y);
		}
	}

	private static void drawVerticalHexGrid(Painter p, int width, int height, float cellWidth, float xOffset, float yOffset)
	{
		float size = cellWidth / (float) Math.sqrt(3);
		float hexHeight = size * 2;
		float hexWidth = (float) Math.sqrt(3) * size;
		float vertSpacing = 0.75f * hexHeight;

		for (float y = yOffset; y < height + hexHeight; y += vertSpacing)
		{
			boolean offset = ((int) ((y - yOffset) / vertSpacing)) % 2 == 1;
			for (float x = xOffset + (offset ? hexWidth / 2 : 0); x < width + hexWidth; x += hexWidth)
			{
				drawHex(p, x, y, size, true);
			}
		}
	}

	private static void drawHorizontalHexGrid(Painter p, int width, int height, float cellWidth, float xOffset, float yOffset)
	{
		float size = cellWidth / 2f;
		float hexWidth = size * 2;
		float hexHeight = (float) Math.sqrt(3) * size;
		float horizSpacing = 0.75f * hexWidth;

		for (float x = xOffset; x < width + hexWidth; x += horizSpacing)
		{
			boolean offset = ((int) ((x - xOffset) / horizSpacing)) % 2 == 1;
			for (float y = yOffset + (offset ? hexHeight / 2 : 0); y < height + hexHeight; y += hexHeight)
			{
				drawHex(p, x, y, size, false);
			}
		}
	}

	private static void drawHex(Painter p, float cx, float cy, float size, boolean vertical)
	{
		List<FloatPoint> hex = new ArrayList<>(6);
		for (int i = 0; i < 6; i++)
		{
			double angle = Math.toRadians((!vertical ? 60 : 30) + i * 60);
			float x = (float) (cx + size * Math.cos(angle));
			float y = (float) (cy + size * Math.sin(angle));
			hex.add(new FloatPoint(x, y));
		}
		p.drawPolygonFloat(hex);
	}

}

