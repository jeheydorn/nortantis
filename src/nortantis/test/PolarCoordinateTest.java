package nortantis.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import hoten.geom.Point;
import nortantis.PolarCoordinate;

public class PolarCoordinateTest
{

	@Test
	public void convertToCartesianTest()
	{
		{
			PolarCoordinate target = new PolarCoordinate(1.862266363, 10.4);
			Point expected = new Point(-3, 10);
			Point actual = target.toCartesian();
			assertEquals(expected.x, actual.x, 0.1);
			assertEquals(expected.y, actual.y, 0.1);
		}

		{
			PolarCoordinate target = new PolarCoordinate(3.403392041, 12);
			Point expected = new Point(-11.59, -3.11);
			Point actual = target.toCartesian();
			assertEquals(expected.x, actual.x, 0.1);
			assertEquals(expected.y, actual.y, 0.1);
		}
	}

}
