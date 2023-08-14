package nortantis.editor;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.swing.MapEdits;
import nortantis.swing.UpdateType;
import nortantis.util.Logger;

public abstract class MapUpdater
{
	private boolean mapNeedsFullRedraw;
	private ArrayDeque<IncrementalUpdate> incrementalUpdatesToDraw;
	public boolean isMapBeingDrawn;
	private ReentrantLock drawLock;
	public MapParts mapParts;
	private boolean createEditsIfNotPresent;
	private Dimension maxMapSize;

	
	public MapUpdater(boolean createEditsIfNotPresent)
	{
		drawLock = new ReentrantLock();
		incrementalUpdatesToDraw = new ArrayDeque<>();
		this.createEditsIfNotPresent = createEditsIfNotPresent;

	}

	/**
	 * Redraws the map, then displays it. Use only with UpdateType.Full and
	 * UpdateType.Quick.
	 */
	public void createAndShowMapFull()
	{
		createAndShowMap(UpdateType.Full, null, null);
	}

	public void createAndShowMapIncrementalUsingCenters(Set<Center> centersChanged)
	{
		createAndShowMap(UpdateType.Incremental, centersChanged, null);
	}

	public void createAndShowMapIncrementalUsingEdges(Set<Edge> edgesChanged)
	{
		createAndShowMap(UpdateType.Incremental, null, edgesChanged);
	}

	public void createAndShowMapFromChange(MapChange change)
	{
		if (change.updateType == UpdateType.Full)
		{
			createAndShowMapFull();
		}
		else
		{
			Set<Center> centersChanged = getCentersWithChangesInEdits(change.edits);
			Set<Edge> edgesChanged = null;
			// Currently createAndShowMap doesn't support drawing both center
			// edits and edge edits at the same time, so there is no
			// need to find edges changed if centers were changed.
			if (centersChanged.size() == 0)
			{
				edgesChanged = getEdgesWithChangesInEdits(change.edits);
			}
			createAndShowMap(UpdateType.Incremental, centersChanged, edgesChanged);
		}
	}

	private Set<Center> getCentersWithChangesInEdits(MapEdits changeEdits)
	{
		Set<Center> changedCenters = getEdits().centerEdits.stream()
				.filter(cEdit -> !cEdit.equals(changeEdits.centerEdits.get(cEdit.index)))
				.map(cEdit -> mapParts.graph.centers.get(cEdit.index)).collect(Collectors.toSet());

		Set<RegionEdit> regionChanges = getEdits().regionEdits.values().stream()
				.filter(rEdit -> !rEdit.equals(changeEdits.regionEdits.get(rEdit.regionId))).collect(Collectors.toSet());
		for (RegionEdit rEdit : regionChanges)
		{
			Set<Center> regionCenterEdits = changeEdits.centerEdits.stream()
					.filter(cEdit -> cEdit.regionId != null && cEdit.regionId == rEdit.regionId)
					.map(cEdit -> mapParts.graph.centers.get(cEdit.index)).collect(Collectors.toSet());
			changedCenters.addAll(regionCenterEdits);
		}

		return changedCenters;
	}

	private Set<Edge> getEdgesWithChangesInEdits(MapEdits changeEdits)
	{
		return getEdits().edgeEdits.stream().filter(eEdit -> !eEdit.equals(changeEdits.edgeEdits.get(eEdit.index)))
				.map(eEdit -> mapParts.graph.edges.get(eEdit.index)).collect(Collectors.toSet());
	}

	/**
	 * Redraws the map, then displays it
	 */
	private void createAndShowMap(UpdateType updateType, Set<Center> centersChanged, Set<Edge> edgesChanged)
	{
		if (isMapBeingDrawn)
		{
			if (updateType == UpdateType.Full)
			{
				mapNeedsFullRedraw = true;
				incrementalUpdatesToDraw.clear();
			}
			else if (updateType == UpdateType.Incremental)
			{
				incrementalUpdatesToDraw.add(new IncrementalUpdate(centersChanged, edgesChanged));
			}
			return;
		}

		isMapBeingDrawn = true;
		onBeginDraw();
		
		final MapSettings settings = getSettingsFromGUI();
		settings.alwaysUpdateLandBackgroundWithOcean = true;
		
		if (createEditsIfNotPresent && settings.edits.isEmpty())
		{
			settings.edits.bakeGeneratedTextAsEdits = true;
		}

		// TODO remove these and draw everything
		settings.frayedBorder = false;
		settings.drawText = false;
		settings.grungeWidth = 0;
		settings.drawBorder = false;

		SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>()
		{
			@Override
			public BufferedImage doInBackground() throws IOException
			{				
				Logger.clear();
				drawLock.lock();
				try
				{
					if (updateType == UpdateType.Full)
					{
						if (mapParts == null)
						{
							mapParts = new MapParts();
						}
						BufferedImage map = new MapCreator().createMap(settings, maxMapSize, mapParts);
						System.gc();
						return map;
					}
					else
					{
						BufferedImage map = getCurrentMapForIncrementalUpdate();
						// Incremental update
						if (centersChanged != null && centersChanged.size() > 0)
						{
							new MapCreator().incrementalUpdateCenters(settings, mapParts, map, centersChanged);
							return map;
						}
						else if (edgesChanged != null && edgesChanged.size() > 0)
						{
							new MapCreator().incrementalUpdateEdges(settings, mapParts, map, edgesChanged);
							return map;
						}
						else
						{
							// Nothing to do.
							return map;
						}
					}
				}
				finally
				{
					drawLock.unlock();
				}
			}

			@Override
			public void done()
			{
				BufferedImage map = null;
				try
				{
					 map = get();
				}
				catch (InterruptedException ex)
				{
					throw new RuntimeException(ex);
				}
				catch (Exception ex)
				{
					if (isCausedByOutOfMemoryError(ex))
					{
						ex.printStackTrace();
						String outOfMemoryMessage = "Out of memory. Try lowering the zoom or allocating more memory to the Java heap space.";
						JOptionPane.showMessageDialog(null, outOfMemoryMessage, "Error", JOptionPane.ERROR_MESSAGE);
					}
					else
					{
						ex.printStackTrace();
						JOptionPane.showMessageDialog(null, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
					}
				}
				
				if (map != null)
				{
					if (createEditsIfNotPresent)
					{
						initializeCenterEditsIfEmpty();
						initializeRegionEditsIfEmpty();
						initializeEdgeEditsIfEmpty();
					}
										
					onFinishedDrawing(map);
					
					isMapBeingDrawn = false;
					if (mapNeedsFullRedraw)
					{
						createAndShowMapFull();
					}
					else if (updateType == UpdateType.Incremental && incrementalUpdatesToDraw.size() > 0)
					{
						IncrementalUpdate incrementalUpdate = combineAndGetNextIncrementalUpdateToDraw();
						createAndShowMap(UpdateType.Incremental, incrementalUpdate.centersChanged, incrementalUpdate.edgesChanged);
					}

					if (updateType == UpdateType.Full)
					{
						mapNeedsFullRedraw = false;
					}
				}
				else
				{
					onFailedToDraw();
				}


	
			}

		};
		worker.execute();
	}
	
	protected abstract void onBeginDraw();
	
	protected abstract MapSettings getSettingsFromGUI();
	
	protected abstract void onFinishedDrawing(BufferedImage map);
	
	protected abstract void onFailedToDraw();

	protected abstract MapEdits getEdits();
	
	protected abstract BufferedImage getCurrentMapForIncrementalUpdate();

	/**
	 * Combines the incremental updates in incrementalUpdatesToDraw so they can
	 * be drawn together. Clears out incrementalUpdatesToDraw.
	 * 
	 * @return The combined update to draw
	 */
	private IncrementalUpdate combineAndGetNextIncrementalUpdateToDraw()
	{
		if (incrementalUpdatesToDraw.size() == 0)
		{
			return null;
		}

		IncrementalUpdate result = incrementalUpdatesToDraw.pop();
		if (incrementalUpdatesToDraw.size() == 1)
		{
			return result;
		}

		while (incrementalUpdatesToDraw.size() > 0)
		{
			IncrementalUpdate next = incrementalUpdatesToDraw.pop();
			result.add(next);
		}
		return result;
	}
	
	private void initializeCenterEditsIfEmpty()
	{
		if (getEdits().centerEdits.isEmpty())
		{
			getEdits().initializeCenterEdits(mapParts.graph.centers, mapParts.iconDrawer);
		}
	}

	private void initializeEdgeEditsIfEmpty()
	{
		if (getEdits().edgeEdits.isEmpty())
		{
			getEdits().initializeEdgeEdits(mapParts.graph.edges);
		}
	}

	private void initializeRegionEditsIfEmpty()
	{
		if (getEdits().regionEdits.isEmpty())
		{
			getEdits().initializeRegionEdits(mapParts.graph.regions.values());
		}
	}
	

	private boolean isCausedByOutOfMemoryError(Throwable ex)
	{
		if (ex == null)
		{
			return false;
		}

		if (ex instanceof OutOfMemoryError)
		{
			return true;
		}

		return isCausedByOutOfMemoryError(ex.getCause());
	}
	
	public void setMaxMapSize(Dimension dimension)
	{
		maxMapSize = dimension;
	}

	private class IncrementalUpdate
	{
		public IncrementalUpdate(Set<Center> centersChanged, Set<Edge> edgesChanged)
		{
			if (centersChanged != null)
			{
				this.centersChanged = new HashSet<Center>(centersChanged);
			}
			if (edgesChanged != null)
			{
				this.edgesChanged = new HashSet<Edge>(edgesChanged);
			}
		}

		Set<Center> centersChanged;
		Set<Edge> edgesChanged;

		public void add(IncrementalUpdate other)
		{
			if (other == null)
			{
				return;
			}

			if (centersChanged != null && other.centersChanged != null)
			{
				centersChanged.addAll(other.centersChanged);
			}
			else if (centersChanged == null && other.centersChanged != null)
			{
				centersChanged = new HashSet<>(other.centersChanged);
			}

			if (edgesChanged != null && other.edgesChanged != null)
			{
				edgesChanged.addAll(other.edgesChanged);
			}
			else if (edgesChanged == null && other.edgesChanged != null)
			{
				edgesChanged = new HashSet<>(other.edgesChanged);
			}
		}
	}
}
