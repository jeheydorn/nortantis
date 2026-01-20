package nortantis.platform.skia;

/**
 * Tracks where image data is currently stored and whether CPU/GPU copies are in sync.
 */
public enum ImageLocation
{
	/**
	 * Image data only exists in CPU Bitmap. Used for small images or when GPU is unavailable.
	 */
	CPU_ONLY,

	/**
	 * Image data only exists in GPU texture. Rare case for render-only images.
	 */
	GPU_ONLY,

	/**
	 * CPU bitmap has been modified, GPU texture is stale/invalid.
	 */
	CPU_DIRTY,

	/**
	 * GPU texture has been modified (via drawing), CPU bitmap is stale.
	 */
	GPU_DIRTY,

	/**
	 * Both CPU bitmap and GPU texture contain the same data.
	 */
	SYNCHRONIZED
}
