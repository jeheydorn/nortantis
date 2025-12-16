package nortantis.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import nortantis.geom.Point;

public class GeometryHelperTest
{

	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
	}

	@Test
	public void testLineOverlapsCircle_Below()
	{
		Point p1 = new Point(1, 1);
		Point p2 = new Point(5, 5);
		Point circleCenter = new Point(4, 0);
		double radius = 2.828;

		boolean result = GeometryHelper.doesLineOverlapCircle(p1, p2, circleCenter, radius);
		assertFalse("The line is tangent to the circle and should overlap.", result);
	}

	@Test
	public void testLineOverlapsCircle_Intersects()
	{
		Point p1 = new Point(1, 1);
		Point p2 = new Point(5, 5);
		Point circleCenter = new Point(3, 3);
		double radius = 0.5;

		boolean result = GeometryHelper.doesLineOverlapCircle(p1, p2, circleCenter, radius);
		assertTrue("The line intersects the circle and should overlap.", result);
	}

	@Test
	public void testLineDoesNotOverlapCircle_Outside()
	{
		Point p1 = new Point(1, 1);
		Point p2 = new Point(5, 5);
		Point circleCenter = new Point(10, 10);
		double radius = 1.0;

		boolean result = GeometryHelper.doesLineOverlapCircle(p1, p2, circleCenter, radius);
		assertFalse("The line is outside the circle and should not overlap.", result);
	}

	@Test
	public void testLineOverlapsCircle_Above()
	{
		Point p1 = new Point(1, 1);
		Point p2 = new Point(5, 5);
		Point circleCenter = new Point(3, 5);
		double radius = 1.0;

		boolean result = GeometryHelper.doesLineOverlapCircle(p1, p2, circleCenter, radius);
		assertFalse("The line passes through the center of the circle and should overlap.", result);
	}

	@Test
	public void testLineTouchesCircle_AtEdge()
	{
		Point p1 = new Point(3, 0);
		Point p2 = new Point(3, 6);
		Point circleCenter = new Point(6, 3);
		double radius = 3.0;

		boolean result = GeometryHelper.doesLineOverlapCircle(p1, p2, circleCenter, radius);
		assertTrue("The line touches the circle at the edge and should overlap.", result);
	}

	@Test
	public void testLineTouchesCircle_partWayInAboveReversed()
	{
		Point p1 = new Point(3, 3);
		Point p2 = new Point(1, 1);
		Point circleCenter = new Point(3.5, 3.5);
		double radius = 1.0;

		boolean result = GeometryHelper.doesLineOverlapCircle(p1, p2, circleCenter, radius);
		assertTrue("The line touches the circle at the edge and should overlap.", result);
	}

	@Test
	public void testLineTouchesCircle_partWayInAbove()
	{
		Point p1 = new Point(1, 1);
		Point p2 = new Point(3, 3);
		Point circleCenter = new Point(3.5, 3.5);
		double radius = 1.0;

		boolean result = GeometryHelper.doesLineOverlapCircle(p1, p2, circleCenter, radius);
		assertTrue("The line touches the circle at the edge and should overlap.", result);
	}

	@Test
	public void testLineTouchesCircle_partWayInBelow()
	{
		Point p1 = new Point(3, 3);
		Point p2 = new Point(1, 1);
		Point circleCenter = new Point(0.5, 0.5);
		double radius = 1.0;

		boolean result = GeometryHelper.doesLineOverlapCircle(p1, p2, circleCenter, radius);
		assertTrue("The line touches the circle at the edge and should overlap.", result);
	}
}
