package nortantis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import nortantis.geom.FloatPoint;
import nortantis.geom.IntDimension;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.platform.Color;
import nortantis.platform.DrawQuality;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.util.ImageHelper;

public class GridDrawer
{
	public static void drawGrid(Image image, MapSettings settings, Rectangle drawBounds, IntDimension mapDimensions, WorldGraph graph, Collection<Center> centersToDraw)
	{
		int alpha = settings.gridOverlayColor.getAlpha();

		Image hexImage = Image.create(image.getWidth(), image.getHeight(), ImageType.ARGB);
		ImageHelper.setAlphaOfAllPixels(hexImage, 0);

		{
			Painter p = hexImage.createPainter(DrawQuality.High);
			p.setColor(Color.create(settings.gridOverlayColor.getRed(), settings.gridOverlayColor.getGreen(), settings.gridOverlayColor.getBlue()));
			float lineWidth = settings.gridOverlayLineWidth * (float) settings.resolution;
			p.setBasicStroke(lineWidth);
			if (drawBounds != null && settings.gridOverlayShape != GridOverlayShape.Voronoi_polygons)
			{
				p.translate(-drawBounds.x, -drawBounds.y);
			}

			float width = mapDimensions.width;
			float height = mapDimensions.height;

			switch (settings.gridOverlayShape)
			{
				case Squares -> drawSquareGrid(p, width, height, settings.gridOverlayRowOrColCount, settings.gridOverlayXOffset, settings.gridOverlayYOffset);
				case Vertical_hexes -> drawVerticalHexGrid(p, width, height, settings.gridOverlayRowOrColCount, settings.gridOverlayXOffset, settings.gridOverlayYOffset, drawBounds, lineWidth);
				case Horizontal_hexes -> drawHorizontalHexGrid(p, width, height, settings.gridOverlayRowOrColCount, settings.gridOverlayXOffset, settings.gridOverlayYOffset, drawBounds, lineWidth);
				case Voronoi_polygons -> drawVoronoiOnLand(p, graph, centersToDraw, drawBounds, settings.drawVoronoiGridOverlayOnlyOnLand);
				default -> throw new IllegalArgumentException("Unexpected value: " + settings.gridOverlayShape);
			}

			p.dispose();
		}

		{
			Painter p = image.createPainter();
			p.drawImage(ImageHelper.applyAlpha(hexImage, alpha), 0, 0);
			p.dispose();
		}
	}

	private static void drawVoronoiOnLand(Painter p, WorldGraph graph, Collection<Center> centersToDraw, Rectangle drawBounds, boolean drawOnlyOnLand)
	{
		graph.drawVoronoi(p, centersToDraw, drawBounds, drawOnlyOnLand);
	}

	private static void drawSquareGrid(Painter p, float width, float height, int colCount, GridOverlayOffset xOffset, GridOverlayOffset yOffset)
	{
		float cellWidth = width / colCount;
		float xOffsetFloat = xOffset.getScale() * cellWidth - cellWidth;
		float yOffsetFloat = yOffset.getScale() * cellWidth - cellWidth;

		for (float x = xOffsetFloat; x <= width + 1; x += cellWidth)
		{
			p.drawLine(x, 0, x, height);
		}
		for (float y = yOffsetFloat; y <= height + 1; y += cellWidth)
		{
			p.drawLine(0, y, width, y);
		}
	}

	private static void drawVerticalHexGrid(Painter p, float width, float height, int colCount, GridOverlayOffset xOffset, GridOverlayOffset yOffset, Rectangle drawBounds, float lineWidth)
	{
		float hexWidth = width / colCount;
		float hexHeight = (hexWidth / (float) Math.sqrt(3)) * 2;
		float vertSpacing = 0.75f * hexHeight;
		float xOffsetFloat = xOffset.getScale() * hexWidth - hexWidth;
		float yOffsetFloat = yOffset.getScale() * hexHeight + hexHeight / 2f - vertSpacing * 2f;
		int rowNumber = 0;

		for (float y = yOffsetFloat; y < height + hexHeight; y += vertSpacing)
		{
			rowNumber++;
			boolean offset = rowNumber % 2 == 1;
			for (float x = xOffsetFloat + (offset ? hexWidth / 2 : 0); x < width + hexWidth; x += hexWidth)
			{
				if (x <= 0 || !(x + hexWidth < width + hexWidth))
				{
					continue;
				}
				if (drawBounds != null && !drawBounds.overlaps(new Rectangle(x - hexWidth / 2.0, y - hexWidth / 2.0, hexWidth, hexHeight).pad(lineWidth, lineWidth)))
				{
					continue;
				}
				drawHex(p, x, y, hexHeight / 2f, true);
			}
		}
	}

	private static void drawHorizontalHexGrid(Painter p, float width, float height, int rowCount, GridOverlayOffset xOffset, GridOverlayOffset yOffset, Rectangle drawBounds, float lineWidth)
	{
		float hexHeight = height / rowCount;
		float hexWidth = (hexHeight / (float) Math.sqrt(3)) * 2;
		float horizSpacing = 0.75f * hexWidth;
		float yOffsetFloat = yOffset.getScale() * hexHeight - hexHeight;
		float xOffsetFloat = xOffset.getScale() * hexWidth + hexWidth / 2f - horizSpacing * 2f;
		int colNumber = 0;

		for (float x = xOffsetFloat; x < width + hexWidth; x += horizSpacing)
		{
			colNumber++;
			boolean offset = colNumber % 2 == 1;
			for (float y = yOffsetFloat + (offset ? hexHeight / 2 : 0); y < height + hexHeight; y += hexHeight)
			{
				if (y <= 0 || !(y + hexHeight < height + hexHeight))
				{
					continue;
				}
				if (drawBounds != null && !drawBounds.overlaps(new Rectangle(x - hexWidth / 2.0, y - hexWidth / 2.0, hexWidth, hexHeight).pad(lineWidth, lineWidth)))
				{
					continue;
				}
				drawHex(p, x, y, hexWidth / 2f, false);
			}
		}
	}

	private static void drawHex(Painter p, float cx, float cy, float size, boolean vertical)
	{
		List<FloatPoint> hex = new ArrayList<>(6);
		for (int i = 0; i < 6; i++)
		{
			double angle = Math.toRadians((vertical ? 30 : 60) + i * 60);
			float x = (float) (cx + size * Math.cos(angle));
			float y = (float) (cy + size * Math.sin(angle));
			hex.add(new FloatPoint(x, y));
		}
		p.drawPolygonFloat(hex);
	}

}
