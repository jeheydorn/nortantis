package nortantis.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.simple.JSONArray;
import org.json.simple.JSONValue;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

import nortantis.FontFinder;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.TextType;
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
 * Makes maps in a web page or web worker and sends them to the page. The page starts it with "commands", a JSON array of commands, and the
 * resolution to draw at. Each command is an object with a name and, for some, a value (see {@link DemoSession#applyAll}). It applies the
 * commands to the map's settings, sends the settings to the page, then draws the map and sends it, followed by its saved settings.
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

	private static boolean isSetUp;
	private static final DemoSession session = new DemoSession();

	public static void main(String[] args)
	{
		if (args[0].equals("job"))
		{
			runJob(Integer.parseInt(args[1]), args[2], args[3]);
			return;
		}
		if (!args[0].equals("commands"))
		{
			throw new IllegalArgumentException("Unrecognized mode: " + args[0]);
		}

		if (!isSetUp)
		{
			setUp(canUseHelper() ? new WorkerJobs() : null);
			FontFinder.ensureInitialized();
			// The browser loads registered fonts in the background, so wait for them before drawing any text.
			waitForFonts();
			isSetUp = true;
		}

		// A failed command still leaves a map to draw, so the page shows the settings the map has.
		RuntimeException failure = null;
		try
		{
			session.applyAll((JSONArray) JSONValue.parse(args[1]));
		}
		catch (RuntimeException e)
		{
			failure = e;
		}
		showSettings(session.describe());
		drawMap(Double.parseDouble(args[2]));
		if (failure != null)
		{
			throw failure;
		}
	}

	private static void drawMap(double resolution)
	{
		MapSettings settings = session.copySettingsForDrawing();
		settings.resolution = resolution;
		startedDrawing();
		MapParts mapParts = new MapParts();
		Image map = new MapCreator().createMap(settings, null, mapParts);
		showMap(map.getWidth(), map.getHeight(), ((WebImage) map).toPackedARGB(), map.getType() != ImageType.ARGB, findTitle(settings));
		save(settings, mapParts);
	}

	/**
	 * The map's title, which drawing it adds to its text, or null if it has none.
	 */
	private static String findTitle(MapSettings settings)
	{
		for (MapText text : settings.edits.text)
		{
			if (text.type == TextType.Title && text.value != null && !text.value.trim().isEmpty())
			{
				return text.value.trim();
			}
		}
		return null;
	}

	/**
	 * Sets up Nortantis to run in this worker or page.
	 *
	 * @param separateMemoryJobs
	 *            Where to create grunge and the images a background generates, or null to create them here.
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
			if (!isSetUp)
			{
				setUp(null);
				isSetUp = true;
			}
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

	/**
	 * Sends the settings of the map that was just drawn to the page, as a .nort file.
	 */
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
		// A saved map's resolution is the resolution it exports at.
		settings.resolution = MapSettings.defaultResolution;
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
	@JSBody(script = "return self.nortantisMemoryLimit || (self.performance && performance.memory && performance.memory.jsHeapSizeLimit) || 2147483648;")
	private static native double getMemoryLimit();

	/**
	 * Whether a helper worker can do part of each map, which it can only when this runs in a worker.
	 */
	@JSBody(script = "return nortantisJobs.canUseHelper;")
	private static native boolean canUseHelper();

	@Async
	private static native void waitForFonts();

	private static void waitForFonts(AsyncCallback<Void> callback)
	{
		whenFontsReady(() -> callback.complete(null));
	}

	@JSBody(params = { "callback" }, script = "nortantisText.whenReady(callback);")
	private static native void whenFontsReady(Callback callback);

	@JSBody(params = { "json" }, script = "nortantisHost.showSettings(json);")
	private static native void showSettings(String json);

	@JSBody(script = "nortantisHost.startedDrawing();")
	private static native void startedDrawing();

	@JSBody(params = { "data" }, script = "nortantisHost.saveSettings(data);")
	private static native void saveSettings(@JSByRef byte[] data);

	@JSBody(params = { "width", "height", "pixels", "isOpaque", "title" }, script = "nortantisHost.showMap(width, height, pixels, isOpaque, title);")
	private static native void showMap(int width, int height, @JSByRef int[] pixels, boolean isOpaque, String title);
}
