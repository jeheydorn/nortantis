package nortantis.test;

import org.junit.Test;

import nortantis.graph.voronoi.VoronoiGraph;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;

public class VoronoiGraphTest
{
	@Test
	public void runPrivateUnitTests()
	{
		// Tell drawing code to use AWT.
		PlatformFactory.setInstance(new AwtFactory());
		
		VoronoiGraph.runPrivateUnitTests();
	}

}
