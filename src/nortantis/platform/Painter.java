package nortantis.platform;

import java.util.List;

import nortantis.Stroke;
import nortantis.geom.IntPoint;
import nortantis.geom.Point;
import nortantis.util.Range;

public abstract class Painter
{
	public abstract void drawImage(Image image, int x, int y);

	public abstract void drawImage(Image image, int x, int y, int width, int height);

	public abstract void setAlphaComposite(nortantis.platform.AlphaComposite composite, float alpha);
	
	public abstract void setAlphaComposite(nortantis.platform.AlphaComposite composite);

	public abstract void setColor(Color color);

	public abstract void drawRect(int x, int y, int width, int height);

	public abstract void dispose();

	public void rotate(double angle, Point pivot)
	{
		rotate(angle, pivot.x, pivot.y);
	}
	
	public abstract void rotate(double angle, double pivotX, double pivotY);

	public abstract void translate(double x, double y);

	public abstract void setFont(Font font);
	
	public abstract void drawString(String string, double x, double y);

	public abstract void setTransform(Transform transform);

	public abstract Transform getTransform();

	public abstract Font getFont();

	public abstract Color getColor();

	public abstract void fillPolygon(int[] xPoints, int[] yPoints);

	public void fillPolygon(List<IntPoint> vertices)
	{
		int[] xPoints = new int[vertices.size()];
		int[] yPoints = new int[vertices.size()];
		for (int i = 0; i < vertices.size(); i++)
		{
			xPoints[i] = vertices.get(i).x;
			yPoints[i] = vertices.get(i).y;
		}
		fillPolygon(xPoints, yPoints);
	}

	public void fillPolygonDouble(List<Point> vertices)
	{
		int[] xPoints = new int[vertices.size()];
		int[] yPoints = new int[vertices.size()];
		for (int i = 0; i < vertices.size(); i++)
		{
			xPoints[i] = (int) vertices.get(i).x;
			yPoints[i] = (int) vertices.get(i).y;
		}
		fillPolygon(xPoints, yPoints);
	}

	public abstract void drawPolygon(int[] xPoints, int[] yPoints);
	
	public void drawPolygon(List<Point> points)
	{
		int[] xPoints = new int[points.size()];
		int[] yPoints = new int[points.size()];
		for (int i : new Range(points.size()))
		{
			xPoints[i] = (int) points.get(i).x;
			yPoints[i] = (int) points.get(i).y;
		}
		drawPolygon(xPoints, yPoints);
	}

	public abstract void drawPolyline(int[] xPoints, int[] yPoints);
	
	public void drawPolyline(List<IntPoint> points)
	{
		int[] xPoints = new int[points.size()];
		int[] yPoints = new int[points.size()];
		for (int i : new Range(points.size()))
		{
			xPoints[i] = points.get(i).x;
			yPoints[i] = points.get(i).y;
		}
		drawPolyline(xPoints, yPoints);
	}

	public abstract void setGradient(float x1, float y1, Color color1, float x2, float y2, Color color2);

	public abstract void setBasicStroke(float width);
	
	public abstract void setStrokeToSolidLineWithNoEndDecorations(float width);

	public abstract void setStroke(Stroke stroke, double resolutionScale);

	public abstract void drawLine(int x1, int y1, int x2, int y2);

	public abstract void drawOval(int x, int y, int width, int height);

	public abstract void fillOval(int x, int y, int width, int height);

	public abstract void fillRect(int x, int y, int width, int height);

	public abstract int stringWidth(String string);
	
	public abstract int charWidth(char c);

	public abstract int getFontAscent();

	public abstract int getFontDescent();

	public abstract void setClip(int x, int y, int width, int height);
}
