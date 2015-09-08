package nortantis;

import static java.lang.System.out;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import util.Pair;
import util.SerializationUtilities;

public class PrintDataForReport
{
	public static void main(String[] args)
	{
		for (String bookName : Arrays.asList("bible", "The Book of Mormon", "The Wonderful Wizard of Oz", "exodus"))
		{
			printBookData(bookName);
			out.println();
		}
	}
	
	@SuppressWarnings("unchecked")
	private static void printBookData(String bookName)
	{
		String placeNameFilename = "assets/" + bookName + "_place_names.ser";
		List<String> placeNames = null;
		try
		{
			placeNames = (List<String>)SerializationUtilities.deserialize(placeNameFilename);
		} 
		catch (ClassNotFoundException | IOException e)
		{
			out.println("Unable to read place name file " + placeNameFilename);
			throw new RuntimeException(e);
		} 
		
		List<Pair<String>> nounAdjectivePairs = null;
		List<Pair<String>> nounVerbPairs = null;
		try
		{
			nounAdjectivePairs = (List<Pair<String>>) SerializationUtilities.deserialize(
					"assets/" + bookName + "_noun_adjective_pairs.ser");
			nounVerbPairs = (List<Pair<String>>) SerializationUtilities.deserialize(
					"assets/" + bookName + "_noun_verb_pairs.ser");
		} 
		catch (ClassNotFoundException | IOException e)
		{
			throw new RuntimeException(e);
		}
		
		out.println("Book: " + bookName);
		out.println("#places: " + placeNames.size());
		out.println("#noun-adjective pairs: " + nounAdjectivePairs.size());
		out.println("#noun-verb pairs: " + nounVerbPairs.size());
		
		
	}
	
}
