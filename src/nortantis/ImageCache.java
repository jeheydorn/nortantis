package nortantis;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.function.Supplier;

import nortantis.util.ConcurrentHashMapF;
import nortantis.util.ImageHelper;

/**
 * Caches icons in memory to avoid recreating or reloading them.
 */
public class ImageCache
{
	private static ImageCache instance;
	
	/**
	 * Maps original images, to scaled width, to scaled images.
	 */
	private ConcurrentHashMapF<BufferedImage, ConcurrentHashMapF<Integer, BufferedImage>> scaledCache; 
	
	/**
	 * Maps file path (or any string key) to images.
	 */
	private ConcurrentHashMapF<String, BufferedImage> fileCache;

	/**
	 * Maps string keys to images generated (not loaded directly from files).
	 */
	private ConcurrentHashMapF<String, BufferedImage> generatedImageCache;

	/**
	 * Singleton
	 */
	private ImageCache()
	{
		scaledCache = new ConcurrentHashMapF<>();
		fileCache = new ConcurrentHashMapF<>();
		generatedImageCache = new ConcurrentHashMapF<>();
	}
	
	public synchronized static ImageCache getInstance()
	{
		if (instance == null)
			instance = new ImageCache();
		return instance;
	}
	
	public BufferedImage getScaledImage(BufferedImage icon, int width)
	{
		// There is a small chance the 2 different threads might both add the same image at the same time, 
		// but if that did happen it would only results in a little bit of duplicated work, not a functional
		// problem.
		return scaledCache.getOrCreate(icon, () -> new ConcurrentHashMapF<>()).getOrCreate(width, 
				() -> ImageHelper.scaleByWidth(icon, width));
	}
	
	public BufferedImage getImageFromFile(Path path)
	{
		return fileCache.getOrCreate(path.toString(), () -> ImageHelper.read(path.toString()));
	}
	
	public boolean containsImageFile(Path path)
	{
		return fileCache.containsKey(path.toString());
	}
	
	/**
	 * Get an image from cache or create it using createFun.
	 */
	public BufferedImage getOrCreateImage(String key, Supplier<BufferedImage> createFun)
	{
		return generatedImageCache.getOrCreate(key.toString(), createFun);
	}
	
	public static void clear()
	{
		getInstance().scaledCache.clear();
		getInstance().fileCache.clear();
		getInstance().generatedImageCache.clear();
	}
}
