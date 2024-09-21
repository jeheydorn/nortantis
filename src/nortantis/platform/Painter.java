package nortantis.platform;

import java.util.List;

import nortantis.Stroke;
import nortantis.geom.IntPoint;
import nortantis.geom.Point;

public abstract class Painter
{
	public abstract void drawImage(Image image, int x, int y);

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

	public abstract void drawString(String string, int x, int y);

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

	public abstract void drawPolygon(int[] xPoints, int[] yPoints);

	public abstract void drawPolyline(int[] xPoints, int[] yPoints);

	public abstract void setGradient(float x1, float y1, Color color1, float x2, float y2, Color color2);

	public abstract void setBasicStroke(float width);
	
	public abstract void setStroke(Stroke stroke, double resolutionScale);

	public abstract void drawLine(int x1, int y1, int x2, int y2);
	
	public abstract void drawOval(int x, int y, int width, int height);

	public abstract void fillOval(int x, int y, int width, int height);

	public abstract void fillRect(int x, int y, int width, int height);
	
	public abstract int stringWidth(String string);
	
	public abstract int getFontAscent();
	
	public abstract int getFontDescent();
}
