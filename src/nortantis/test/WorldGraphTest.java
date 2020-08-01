package nortantis.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import hoten.geom.Point;
import nortantis.WorldGraph;
import nortantis.PolarCoordinate;

public class WorldGraphTest 
{	
	@Test
	public void calcUnilateralLevelOfConvergenceTest() 
	{		
		{
			Point p1 = new Point(1, -1);
			PolarCoordinate p1Velocity = new PolarCoordinate((3.0/4.0)*Math.PI, 0.6);
			Point p2 = new Point(-1, 1);
			PolarCoordinate p2Velocity = new PolarCoordinate(Math.PI/3, 0.9);

			double actual = WorldGraph.calcUnilateralLevelOfConvergence(p1, p1Velocity,
					p2);
			assertTrue(actual > 0);

			actual = WorldGraph.calcUnilateralLevelOfConvergence(p2, p2Velocity,
					p1);
			assertTrue(actual < 0);
		}
		
		{
			Point p1 = new Point(1, -1);
			PolarCoordinate p1Velocity = new PolarCoordinate((3.0/4.0)*Math.PI, 0.6);
			Point p2 = new Point(-1, 1);
			PolarCoordinate p2Velocity = new PolarCoordinate(Math.PI/3, 0.9);

			double actual = WorldGraph.calcUnilateralLevelOfConvergence(p1, p1Velocity,
					p2);
			assertTrue(actual > 0);

			actual = WorldGraph.calcUnilateralLevelOfConvergence(p2, p2Velocity,
					p1);
			assertTrue(actual < 0);
		}

		{
			Point p1 = new Point(-1, 0.5);
			PolarCoordinate p1Velocity = new PolarCoordinate((1.0/3.0)*Math.PI, 0.1);
			Point p2 = new Point(1, 0.5);
			PolarCoordinate p2Velocity = new PolarCoordinate((1.0/3.0)*Math.PI, 0.99);

			double actual = WorldGraph.calcUnilateralLevelOfConvergence(p1, p1Velocity,
					p2);
			assertTrue(actual > 0);

			actual = WorldGraph.calcUnilateralLevelOfConvergence(p2, p2Velocity,
					p1);
			assertTrue(actual < 0);
		}

		{
			Point p1 = new Point(-1, 0.5);
			PolarCoordinate p1Velocity = new PolarCoordinate((1.0/2.0)*Math.PI, 0.1);
			Point p2 = new Point(1, 0.5);
			PolarCoordinate p2Velocity = new PolarCoordinate((1.0/2.0)*Math.PI, 0.99);

			double actual = WorldGraph.calcUnilateralLevelOfConvergence(p1, p1Velocity,
					p2);
			assertEquals(0, actual, 0.00001);

			actual = WorldGraph.calcUnilateralLevelOfConvergence(p2, p2Velocity, p1);
			assertEquals(0, actual, 0.00001);
		}

		{
			Point p1 = new Point(-1, 0.5);
			PolarCoordinate p1Velocity = new PolarCoordinate((1.0/2.0)*Math.PI, 0.0);
			Point p2 = new Point(1, 0.5);
			PolarCoordinate p2Velocity = new PolarCoordinate(0, 0.0);

			double actual = WorldGraph.calcUnilateralLevelOfConvergence(p1, p1Velocity, p2);
			assertEquals(0, actual, 0.00001);

			actual = WorldGraph.calcUnilateralLevelOfConvergence(p2, p2Velocity, p1);
			assertEquals(0, actual, 0.00001);
		}
		
	}
	
	/**
	 * Checks that divergence levels are the same as convergence levels.
	 */
	@Test
	public void diverganceTest() 
	{
		Point p1 = new Point(0, 0);
		PolarCoordinate p1Velocity = new PolarCoordinate(0.0, 1.0);
		Point p2 = new Point(1, 0);
		PolarCoordinate p2Velocity = new PolarCoordinate(Math.PI, 1.0);

		double convergence1 = WorldGraph.calcUnilateralLevelOfConvergence(p1, p1Velocity, p2);

		double convergence2 = WorldGraph.calcUnilateralLevelOfConvergence(p2, p2Velocity, p1);
		
		p1Velocity = new PolarCoordinate(Math.PI, 1.0);
		p2Velocity = new PolarCoordinate(0.0, 1.0);

		double divergence1 = WorldGraph.calcUnilateralLevelOfConvergence(p1, p1Velocity, p2);

		double divergence2 = WorldGraph.calcUnilateralLevelOfConvergence(p2, p2Velocity, p1);

		assertEquals(convergence1 * -1, divergence1, 0.000001);
		assertEquals(convergence2 * -1, divergence2, 0.000001);
	}

}
