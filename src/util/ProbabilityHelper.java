package util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ProbabilityHelper
{
	/**
	 * Samples a categorical distribution
	 */
	public static <T> T sampleCategorical(Random rand, List<Tuple2<Double, T>> distribution)
	{
		if (distribution.size() == 0)
		{
			throw new IllegalArgumentException("The distribution must have at least one value");
		}
		
		if (distribution.size() == 1)
		{
			return distribution.get(0).getSecond();
		}
		
		double totalWeight = distribution.stream().map(tuple -> tuple.getFirst()).mapToDouble(d -> d).sum();
		if (totalWeight == 0)
		{
			throw new IllegalArgumentException("Total weight cannot be 0.");
		}
		double sample = rand.nextDouble() * totalWeight;
		double curWeight = 0;
		for (Tuple2<Double, T> tuple : distribution)
		{
			curWeight += tuple.getFirst();
			if (curWeight >= sample)
			{
				return tuple.getSecond();
			}
		}
		
		// This shouldn't actually happen.
		assert false;
		return distribution.get(distribution.size() - 1).getSecond();
	}
	
	@SuppressWarnings("rawtypes")
	public static List<Tuple2<Double, Enum>> createUniformDistributionOverEnumValues(Enum[] values)
	{
		List<Tuple2<Double, Enum>> distribution = new ArrayList<>(values.length);
		for (Enum value : values)
		{
			distribution.add(new Tuple2<>(1.0, value));
		}
		
		return distribution;
	}
	
	public static void main(String[] args)
	{
		Map<String, Integer> counts = new HashMap<>();
		for (int i : new Range(10000))
		{
			String value = sampleCategorical(new Random(), Arrays.asList(
					 new Tuple2<>(0.1, "first"), 
					 new Tuple2<>(0.5, "second"),
					 new Tuple2<>(0.4, "third")));
			if (!counts.containsKey(value))
			{
				counts.put(value, 0);
			}
			counts.put(value, counts.get(value) + 1);
		}
		System.out.println(counts);
	}
}