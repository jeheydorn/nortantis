package nortantis.test;

/**
 * For running tests that I need to have stop on a breakpoint. JUnit has a bug
 * where it won't stop on a breakpoint in eclipse.
 */
public class TestRunner
{
	public static void main(String[] args)
	{
		new WorldGraphTest().calcUnilateralLevelOfConvergenceTest();
	}
}
