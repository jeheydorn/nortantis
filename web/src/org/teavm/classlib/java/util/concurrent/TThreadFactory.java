package org.teavm.classlib.java.util.concurrent;

/**
 * Stands in for java.util.concurrent.ThreadFactory when compiling to JavaScript.
 */
public interface TThreadFactory
{
	Thread newThread(Runnable r);
}
