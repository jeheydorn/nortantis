package nortantis.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;

import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.WorldGraph;
import nortantis.geom.Point;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.nodename.as3delaunay.Voronoi;

/**
 * Fingerprints the steps of map generation that must come out identically in a browser and on the desktop, so the two can be compared to
 * find where they first differ. Each line is a name and a hash of exact bit patterns.
 */
public class DeterminismTrace
{
	public static List<String> run(MapSettings settings)
	{
		List<String> lines = new ArrayList<>();

		addRandom(lines, "nextInt", r -> r.nextInt(1000));
		addRandom(lines, "nextDouble", r -> Double.doubleToLongBits(r.nextDouble()));
		addRandom(lines, "nextGaussian", r -> Double.doubleToLongBits(r.nextGaussian()));
		addRandom(lines, "nextFloat", r -> Float.floatToIntBits(r.nextFloat()));
		addRandom(lines, "nextFloatAsDouble", r -> Double.doubleToLongBits(r.nextFloat()));
		addRandom(lines, "nextBoolean", r -> r.nextBoolean() ? 1 : 0);
		addRandom(lines, "nextLong", Random::nextLong);
		addRandom(lines, "nextIntUnbounded", Random::nextInt);
		addRandom(lines, "nextIntRange", r -> r.nextInt(-37, 1000));
		addRandom(lines, "nextIntRangePowerOfTwo", r -> r.nextInt(5, 69));
		addRandom(lines, "nextLongBound", r -> r.nextLong(1000000007L));
		addRandom(lines, "nextDoubleRange", r -> Double.doubleToLongBits(r.nextDouble(-2.5, 7.25)));
		addRandom(lines, "shuffle", r ->
		{
			java.util.List<Integer> list = new ArrayList<>();
			for (int i = 0; i < 20; i++)
			{
				list.add(i);
			}
			java.util.Collections.shuffle(list, r);
			return list.hashCode();
		});

		addMath(lines, "sin", Math::sin);
		addMath(lines, "cos", Math::cos);
		addMath(lines, "tan", Math::tan);
		addMath(lines, "asin", x -> Math.asin(x % 1));
		addMath(lines, "acos", x -> Math.acos(x % 1));
		addMath(lines, "atan", Math::atan);
		addMath(lines, "atan2", x -> Math.atan2(x, 1.3));
		addMath(lines, "exp", x -> Math.exp(x % 20));
		addMath(lines, "log", Math::log);
		addMath(lines, "log10", Math::log10);
		addMath(lines, "pow", x -> Math.pow(x, 1.7));
		addMath(lines, "powInt", x -> Math.pow(x, 2));
		addMath(lines, "sqrt", Math::sqrt);
		addMath(lines, "cbrt", Math::cbrt);
		addMath(lines, "hypot", x -> Math.hypot(x, 2.1));
		addMath(lines, "floatDivide", x -> (float) x / 3.7f);
		addMath(lines, "floatToInt", x -> (int) (x * 1000.5));

		Voronoi voronoi = new Voronoi(settings.worldSize, 4096, 4096, new Random(settings.randomSeed));
		long voronoiHash = 17;
		for (Point p : voronoi.siteCoords())
		{
			voronoiHash = voronoiHash * 31 + Double.doubleToLongBits(p.x);
			voronoiHash = voronoiHash * 31 + Double.doubleToLongBits(p.y);
		}
		lines.add("voronoi " + Long.toHexString(voronoiHash));

		WorldGraph graph = MapCreator.createGraph(settings, true);
		long locationHash = 17;
		long elevationHash = 17;
		long waterHash = 17;
		long regionHash = 17;
		for (Center c : graph.centers)
		{
			locationHash = locationHash * 31 + Double.doubleToLongBits(c.loc.x);
			locationHash = locationHash * 31 + Double.doubleToLongBits(c.loc.y);
			elevationHash = elevationHash * 31 + Double.doubleToLongBits(c.elevation);
			waterHash = waterHash * 31 + (c.isWater ? 1 : 0);
			regionHash = regionHash * 31 + (c.region == null ? -1 : c.region.id);
		}
		lines.add("graph centers " + graph.centers.size());
		lines.add("graph locations " + Long.toHexString(locationHash));
		lines.add("graph elevations " + Long.toHexString(elevationHash));
		lines.add("graph water " + Long.toHexString(waterHash));
		lines.add("graph regions " + Long.toHexString(regionHash));
		return lines;
	}

	private static void addRandom(List<String> lines, String name, java.util.function.ToLongFunction<Random> draw)
	{
		Random random = new Random(1);
		long hash = 17;
		for (int i = 0; i < 1000; i++)
		{
			hash = hash * 31 + draw.applyAsLong(random);
		}
		lines.add("random " + name + " " + Long.toHexString(hash));
	}

	private static void addMath(List<String> lines, String name, DoubleUnaryOperator function)
	{
		long hash = 17;
		for (int i = 0; i < 20000; i++)
		{
			double x = i * 0.01371 + 0.1;
			hash = hash * 31 + Double.doubleToLongBits(function.applyAsDouble(x));
		}
		lines.add("math " + name + " " + Long.toHexString(hash));
	}
}
