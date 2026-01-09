package nortantis.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import nortantis.NameCompiler;
import nortantis.util.Function;
import nortantis.util.Helper;
import nortantis.util.Pair;
import nortantis.util.Range;

public class NameCompilerTest
{

	@Test
	public void test()
	{
		final NameCompiler compiler = new NameCompiler(new Random(), new ArrayList<Pair<String>>(), new ArrayList<Pair<String>>());
		// My examples.
		{
			List<String> before = Arrays.asList("travel", "distil", "equal", "bake", "free", "dye", "tiptoe", "running", "wheel", "picnic", "stood", "forgave", "seen", "set");
			List<String> after = Helper.map(before, new Function<String, String>()
			{
				public String apply(String item)
				{
					return compiler.convertVerbToPresentTense(item);
				}
			});
			List<String> expected = Arrays.asList("travelling", // I'm using a British dictionary apparently.
					"distilling", "equaling", "baking", "freeing", "dyeing", "tiptoeing", "running", "wheeling", "picnicking", "standing", "forgiving", "seeing", "setting");
			for (int i : new Range(expected.size()))
			{
				assertEquals(expected.get(i), after.get(i));
			}
		}

		// Examples from text.
		{
			List<String> before = Arrays.asList("redeem", "stretched", "set", "wept", "appeared", "rid", "plucked", "put", "laid", "stand", "send", "speak", "afflict", "looked", "rest");
			List<String> after = Helper.map(before, new Function<String, String>()
			{
				public String apply(String item)
				{
					return compiler.convertVerbToPresentTense(item);
				}
			});
			List<String> expected = Arrays.asList("redeeming", "stretching", "setting", "weeping", "appearing", "riding", "plucking", "putting", "laying", "standing", "sending", "speaking",
					"afflicting", "looking", "resting");
			for (int i : new Range(expected.size()))
			{
				assertEquals(expected.get(i), after.get(i));
			}

		}
	}
}
