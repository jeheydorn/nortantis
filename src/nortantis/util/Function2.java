package nortantis.util;

public interface Function2 <I, R>
{
	public static Function2<Double, Double> ADD = new Function2<Double, Double>()
			{
				@Override
				public Double apply(Double d1, Double d2)
				{
					return d1 + d2;
				}
			};

	public static Function2<Double, Double> MAX = new Function2<Double, Double>()
			{
				@Override
				public Double apply(Double d1, Double d2)
				{
					return Math.max(d1, d2);
				}
			};

	public R apply(I item1, I item2);
}

