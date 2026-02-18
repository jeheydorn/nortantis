package nortantis.platform;

import nortantis.CancelledException;

import java.io.IOException;

public interface BackgroundTask<T>
{
	public T doInBackground() throws IOException, CancelledException;

	public void done(T result);
}
