package nortantis;

import java.awt.image.BufferedImage;

import util.ConcurrentHashMapF;
import util.ImageHelper;

public class ScaledIconCache
{
	private static ScaledIconCache instance;
	
	/**
	 * Maps original images, to scaled width, to scaled images.
	 */
	private ConcurrentHashMapF<BufferedImage, ConcurrentHashMapF<Integer, BufferedImage>> cache; 
	
	/**
	 * Singleton
	 */
	private ScaledIconCache()
	{
		cache = new ConcurrentHashMapF<>();
	}
	
	public synchronized static ScaledIconCache getInstance()
	{
		if (instance == null)
			instance = new ScaledIconCache();
		return instance;
	}
	
	public BufferedImage getScaledIcon(BufferedImage icon, int width)
	{
		// There is a small chance the 2 different threads might both add the same image at the same time, 
		// but if that did happen it would only results in a little bit of duplicated work, not a functional
		// problem.
		return cache.getOrCreate(icon, () -> new ConcurrentHashMapF<>()).getOrCreate(width, 
				() -> ImageHelper.scaleByWidth(icon, width));
	}
	
	public static void clear()
	{
		getInstance().cache.clear();
	}
}
