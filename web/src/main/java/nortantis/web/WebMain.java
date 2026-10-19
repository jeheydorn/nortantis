package nortantis.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
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
import nortantis.platform.PlatformFactory;
import nortantis.platform.web.SeparateMemoryJobs;
import nortantis.platform.web.WebFactory;
import nortantis.platform.web.WebImage;
import nortantis.swing.translation.Translation;
import nortantis.util.Assets;

/**
 * Generates a random map in a web worker and sends it to the page. The worker passes the random seed and the resolution as the first two
 * arguments. A third argument, if given and not empty, is the asset path of a map settings file to draw instead of a random map. A fourth,
 * if it is "trace", prints values that show whether generation matches the JVM's instead. A fifth, if given, is the number of random maps
 * to make one after another, with the following seeds, for timing.
 * <p>
 * A helper worker instead starts it with "job", a job number, the kind of job, and the job's arguments, to do part of another worker's map
 * (see {@link WorkerJobs}).
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
		if (args.length > 0 && args[0].equals("job"))
		{
			runJob(Integer.parseInt(args[1]), args[2], args[3]);
			return;
		}

		long seed = args.length > 0 ? Long.parseLong(args[0]) : 1;
		double resolution = args.length > 1 ? Double.parseDouble(args[1]) : 0.75;

		long start = System.currentTimeMillis();
		setUp(new WorkerJobs());
		FontFinder.ensureInitialized();
		System.out.println("STARTUP " + (System.currentTimeMillis() - start) + " ms");

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
		// The browser loads registered fonts in the background, so wait for them before drawing any text.
		waitForFonts();
		for (int i = 0; i < runs; i++)
		{
			generate(seed + i, resolution, settingsPath);
		}
		System.out.println("DONE");
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

		showMap(map.getWidth(), map.getHeight(), ((WebImage) map).toPackedARGB(), map.getType() != ImageType.ARGB);
		save(settings, mapParts);
	}

	/**
	 * Sets up Nortantis to run in this worker.
	 *
	 * @param separateMemoryJobs
	 *            Where to create grunge and the images a background generates, or null to create them in this worker.
	 */
	private static void setUp(SeparateMemoryJobs separateMemoryJobs)
	{
		// The browser has no user folder or operating system name. These give Nortantis a preferences folder in TeaVM's in-memory file
		// system, which starts out empty, so every preference starts at its default.
		System.setProperty("user.home", "/home");
		System.setProperty("os.name", "Linux");
		PlatformFactory.setInstance(new WebFactory(new BrowserTextRasterizer(), (long) getMemoryLimit(), separateMemoryJobs));
		Assets.setAssetInputStreamProvider(new BrowserAssets());
		Translation.initialize();
	}

	private static void runJob(int jobId, String kind, String arguments)
	{
		try
		{
			setUp(null);
			Image result;
			if (kind.equals(WorkerJobs.grungeJob))
			{
				result = WorkerJobs.parseGrungeParameters(arguments).createGrunge(() ->
				{
				});
			}
			else if (kind.equals(WorkerJobs.backgroundImageJob))
			{
				result = WorkerJobs.parseBackgroundImageParameters(arguments).create();
			}
			else
			{
				throw new IllegalArgumentException("Unrecognized job: " + kind);
			}
			WorkerJobs.sendResult(jobId, result);
		}
		catch (Throwable e)
		{
			e.printStackTrace();
			WorkerJobs.sendFailure(jobId, e.toString());
		}
	}

	private static void save(MapSettings settings, MapParts mapParts)
	{
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
	}

	/**
	 * The most memory the page's JavaScript can use, where the browser says, or a cautious guess where it doesn't. Only a page can ask the
	 * browser, so a worker uses the limit the page sent it in {@code self.nortantisMemoryLimit}.
	 */
	@JSBody(script = "return self.nortantisMemoryLimit || (performance.memory && performance.memory.jsHeapSizeLimit) || 2147483648;")
	private static native double getMemoryLimit();

	@Async
	private static native void waitForFonts();

	private static void waitForFonts(AsyncCallback<Void> callback)
	{
		whenFontsReady(() -> callback.complete(null));
	}

	@JSBody(params = { "callback" }, script = "nortantisText.whenReady(callback);")
	private static native void whenFontsReady(Callback callback);

	@JSBody(params = { "data" }, script = "nortantisHost.saveSettings(data);")
	private static native void saveSettings(@JSByRef byte[] data);

	@JSBody(params = { "width", "height", "pixels", "isOpaque" }, script = "nortantisHost.showMap(width, height, pixels, isOpaque);")
	private static native void showMap(int width, int height, @JSByRef int[] pixels, boolean isOpaque);
}
