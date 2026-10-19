package nortantis.platform.web;

import java.util.concurrent.Future;

import nortantis.BackgroundImageParameters;
import nortantis.GrungeParameters;
import nortantis.platform.Image;

/**
 * Runs work somewhere that shares no memory with this program, such as another web worker, so that it can run at the same time as the
 * rest of the map.
 */
public interface SeparateMemoryJobs
{
	Future<Image> startGrungeCreation(GrungeParameters parameters);

	Future<Image> startBackgroundImageCreation(BackgroundImageParameters parameters);
}
