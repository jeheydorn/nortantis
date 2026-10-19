package nortantis.web;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

import nortantis.GrungeParameters;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.web.SeparateMemoryJobs;
import nortantis.platform.web.WebImage;

/**
 * Runs work in a helper web worker, through the functions the worker provides in {@code self.nortantisJobs}. The helper runs its own copy
 * of Nortantis, which {@link WebMain} starts in job mode, and sends back the resulting image.
 */
class WorkerJobs implements SeparateMemoryJobs
{
	static final String grungeJob = "grunge";

	@Override
	public Future<Image> startGrungeCreation(GrungeParameters parameters)
	{
		String arguments = parameters.backgroundRandomSeed() + " " + parameters.width() + " " + parameters.height() + " " + parameters.grungeWidth() + " "
				+ parameters.resolution();
		return new JobFuture(startJob(grungeJob, arguments), () -> parameters.createGrunge(() ->
		{
		}));
	}

	/**
	 * Parses the arguments {@link #startGrungeCreation} sends to the helper.
	 */
	static GrungeParameters parseGrungeParameters(String arguments)
	{
		String[] parts = arguments.split(" ");
		return new GrungeParameters(Long.parseLong(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
				Double.parseDouble(parts[4]));
	}

	/**
	 * Sends a finished job's image from the helper back to the worker that started the job.
	 */
	static void sendResult(int jobId, Image image)
	{
		WebImage webImage = (WebImage) image;
		Object pixels = webImage.getPixelArray();
		String type = image.getType().name();
		if (pixels instanceof int[])
		{
			sendIntResult(jobId, image.getWidth(), image.getHeight(), type, (int[]) pixels);
		}
		else if (pixels instanceof short[])
		{
			sendShortResult(jobId, image.getWidth(), image.getHeight(), type, (short[]) pixels);
		}
		else
		{
			sendByteResult(jobId, image.getWidth(), image.getHeight(), type, (byte[]) pixels);
		}
	}

	static void sendFailure(int jobId, String message)
	{
		sendFailureToWorker(jobId, message);
	}

	@JSFunctor
	interface JobDoneCallback extends JSObject
	{
		/**
		 * @param failure
		 *            Why the job failed, or null if it succeeded.
		 */
		void done(int width, int height, String type, String failure);
	}

	/**
	 * A job's image, which {@link #get} waits for without blocking the browser. If the helper fails, {@link #get} does the job itself.
	 */
	private static class JobFuture implements Future<Image>
	{
		private final int jobId;
		private final Supplier<Image> doJobHere;
		private Image result;
		private RuntimeException failure;
		private boolean isDone;

		JobFuture(int jobId, Supplier<Image> doJobHere)
		{
			this.jobId = jobId;
			this.doJobHere = doJobHere;
		}

		@Override
		public Image get() throws ExecutionException
		{
			if (!isDone)
			{
				try
				{
					result = waitForJob(jobId);
				}
				catch (RuntimeException helperFailure)
				{
					System.out.println("Doing job " + jobId + " here instead: " + helperFailure.getMessage());
					try
					{
						result = doJobHere.get();
					}
					catch (RuntimeException e)
					{
						failure = e;
					}
				}
				isDone = true;
			}
			if (failure != null)
			{
				throw new ExecutionException(failure);
			}
			return result;
		}

		@Override
		public Image get(long timeout, TimeUnit unit) throws ExecutionException
		{
			return get();
		}

		@Override
		public boolean isDone()
		{
			return isDone;
		}

		@Override
		public boolean isCancelled()
		{
			return false;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning)
		{
			return false;
		}
	}

	@Async
	private static native Image waitForJob(int jobId);

	private static void waitForJob(int jobId, AsyncCallback<Image> callback)
	{
		whenJobDone(jobId, (width, height, type, failure) ->
		{
			if (failure != null)
			{
				callback.error(new RuntimeException("A helper worker failed: " + failure));
				return;
			}
			WebImage image = new WebImage(width, height, ImageType.valueOf(type));
			Object pixels = image.getPixelArray();
			if (pixels instanceof int[])
			{
				copyIntResult(jobId, (int[]) pixels);
			}
			else if (pixels instanceof short[])
			{
				copyShortResult(jobId, (short[]) pixels);
			}
			else
			{
				copyByteResult(jobId, (byte[]) pixels);
			}
			callback.complete(image);
		});
	}

	@JSBody(params = { "kind", "arguments" }, script = "return nortantisJobs.start(kind, arguments);")
	private static native int startJob(String kind, String arguments);

	@JSBody(params = { "jobId", "callback" }, script = "nortantisJobs.whenDone(jobId, callback);")
	private static native void whenJobDone(int jobId, JobDoneCallback callback);

	@JSBody(params = { "jobId", "destination" }, script = "nortantisJobs.copyResult(jobId, destination);")
	private static native void copyIntResult(int jobId, @JSByRef int[] destination);

	@JSBody(params = { "jobId", "destination" }, script = "nortantisJobs.copyResult(jobId, destination);")
	private static native void copyShortResult(int jobId, @JSByRef short[] destination);

	@JSBody(params = { "jobId", "destination" }, script = "nortantisJobs.copyResult(jobId, destination);")
	private static native void copyByteResult(int jobId, @JSByRef byte[] destination);

	@JSBody(params = { "jobId", "width", "height", "type", "pixels" }, script = "nortantisJobs.finish(jobId, width, height, type, pixels);")
	private static native void sendIntResult(int jobId, int width, int height, String type, @JSByRef int[] pixels);

	@JSBody(params = { "jobId", "width", "height", "type", "pixels" }, script = "nortantisJobs.finish(jobId, width, height, type, pixels);")
	private static native void sendShortResult(int jobId, int width, int height, String type, @JSByRef short[] pixels);

	@JSBody(params = { "jobId", "width", "height", "type", "pixels" }, script = "nortantisJobs.finish(jobId, width, height, type, pixels);")
	private static native void sendByteResult(int jobId, int width, int height, String type, @JSByRef byte[] pixels);

	@JSBody(params = { "jobId", "message" }, script = "nortantisJobs.fail(jobId, message);")
	private static native void sendFailureToWorker(int jobId, String message);
}
