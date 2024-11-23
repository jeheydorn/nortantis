package nortantis.platform;

import java.io.IOException;

import nortantis.CancelledException;

public interface BackgroundTask<T>
{
	public T doInBackground() throws IOException, CancelledException;

	public void done(T result);
}
