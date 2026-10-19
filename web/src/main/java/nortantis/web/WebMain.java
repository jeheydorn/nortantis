package nortantis.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

import nortantis.FontFinder;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.SettingsGenerator;
import nortantis.editor.MapParts;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.PixelReader;
import nortantis.platform.PlatformFactory;
import nortantis.platform.web.WebFactory;
import nortantis.swing.translation.Translation;
import nortantis.util.Assets;

/**
 * Generates a random map in a web browser and shows it on the page. The page passes the random seed and the resolution as the first two
 * arguments. A third argument, if given and not empty, is the asset path of a map settings file to draw instead of a random map. A fourth,
 * if given, is the number of random maps to make one after another, for timing.
 */
public class WebMain
{
	@JSFunctor
	interface Callback extends JSObject
	{
		void run();
	}

	public static void main(String[] args)
	{
		long seed = args.length > 0 ? Long.parseLong(args[0]) : 1;
		double resolution = args.length > 1 ? Double.parseDouble(args[1]) : 0.75;

		long start = System.currentTimeMillis();
		// The browser has no user folder or operating system name. These give Nortantis a preferences folder in TeaVM's in-memory file
		// system, which starts out empty, so every preference starts at its default.
		System.setProperty("user.home", "/home");
		System.setProperty("os.name", "Linux");
		PlatformFactory.setInstance(new WebFactory(new BrowserTextRasterizer(), (long) getMemoryLimit()));
		Assets.setAssetInputStreamProvider(new BrowserAssets());
		Translation.initialize();
		FontFinder.ensureInitialized();
		System.out.println("STARTUP " + (System.currentTimeMillis() - start) + " ms");

		// The browser loads registered fonts in the background, so wait for them before drawing any text.
		String settingsPath = args.length > 2 && !args[2].isEmpty() ? args[2] : null;
		int runs = args.length > 4 ? Integer.parseInt(args[4]) : 1;
		if (args.length > 3 && args[3].equals("trace"))
		{
			MapSettings settings = new MapSettings(settingsPath);
			settings.resolution = resolution;
			for (String line : DeterminismTrace.run(settings))
			{
				System.out.println("TRACE " + line);
			}
			System.out.println("DONE");
			return;
		}
		whenFontsReady(() ->
		{
			for (int i = 0; i < runs; i++)
			{
				generate(seed + i, resolution, settingsPath);
			}
			System.out.println("DONE");
		});
	}

	private static void generate(long seed, double resolution, String settingsPath)
	{
		long start = System.currentTimeMillis();
		MapSettings settings = settingsPath != null ? new MapSettings(settingsPath) : SettingsGenerator.generate(new Random(seed), Assets.installedArtPack, null);
		settings.resolution = resolution;
		System.out.println("SETTINGS " + (System.currentTimeMillis() - start) + " ms");

		start = System.currentTimeMillis();
		MapParts mapParts = new MapParts();
		Image map = new MapCreator().createMap(settings, null, mapParts);
		long elapsed = System.currentTimeMillis() - start;
		System.out.println("MAP " + map.getWidth() + "x" + map.getHeight() + " in " + elapsed + " ms");

		// Record the generated land, water, and regions in the edits, as the editor does after its first draw, so that the saved settings
		// draw the same map anywhere, rather than depending on regenerating it.
		if (settings.edits.centerEdits.isEmpty())
		{
			settings.edits.initializeCenterEdits(mapParts.graph.centers);
		}
		if (settings.edits.regionEdits.isEmpty())
		{
			settings.edits.initializeRegionEdits(mapParts.graph.regions.values());
		}
		try
		{
			String savedPath = "/home/map.nort";
			// Checked first because TeaVM's createDirectories throws when the folder already exists.
			if (!Files.exists(Paths.get(savedPath).getParent()))
			{
				Files.createDirectories(Paths.get(savedPath).getParent());
			}
			settings.writeToFile(savedPath);
			byte[] saved = Files.readAllBytes(Paths.get(savedPath));
			saveSettings(saved);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}

		int[] pixels = new int[map.getWidth() * map.getHeight()];
		try (PixelReader reader = map.createPixelReader())
		{
			for (int y = 0; y < map.getHeight(); y++)
			{
				for (int x = 0; x < map.getWidth(); x++)
				{
					pixels[y * map.getWidth() + x] = reader.getRGB(x, y);
				}
			}
		}
		showMap(map.getWidth(), map.getHeight(), pixels, map.getType() != ImageType.ARGB);
	}

	/**
	 * The most memory the page's JavaScript can use, where the browser says, or a cautious guess where it doesn't.
	 */
	@JSBody(script = "return (performance.memory && performance.memory.jsHeapSizeLimit) || 2147483648;")
	private static native double getMemoryLimit();

	@JSBody(params = { "callback" }, script = "nortantisText.whenReady(callback);")
	private static native void whenFontsReady(Callback callback);

	@JSBody(params = { "data" }, script = "nortantisHost.saveSettings(data);")
	private static native void saveSettings(@JSByRef byte[] data);

	@JSBody(params = { "width", "height", "pixels", "isOpaque" }, script = "nortantisHost.showMap(width, height, pixels, isOpaque);")
	private static native void showMap(int width, int height, @JSByRef int[] pixels, boolean isOpaque);
}
