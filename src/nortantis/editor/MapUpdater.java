package nortantis.editor;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import nortantis.CancelledException;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.NameCreator;
import nortantis.geom.Dimension;
import nortantis.geom.IntRectangle;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.BackgroundTask;
import nortantis.platform.Image;
import nortantis.platform.PlatformFactory;
import nortantis.swing.MapEdits;
import nortantis.swing.UpdateType;
import nortantis.util.Logger;
import nortantis.util.Range;

public abstract class MapUpdater
{
	private boolean isMapBeingDrawn;
	private ReentrantLock drawLock;
	private ReentrantLock interactionsLock;
	public MapParts mapParts;
	private boolean createEditsIfNotPresentAndUseMapParts;
	private Dimension maxMapSize;
	private boolean enabled;
	private boolean isMapReadyForInteractions;
	private Queue<Runnable> tasksToRunWhenMapReady;
	private ArrayDeque<MapUpdate> updatesToDraw;
	private MapCreator currentNonIncrementalMapCreator;

	/**
	 * 
	 * @param createEditsIfNotPresentAndUseMapParts
	 *            When true, drawing the map for the first time will fill in MapSettings.Edits, and a MapParts object will be used. Only set
	 *            this to false if the map will only do full re-draws.
	 */
	public MapUpdater(boolean createEditsIfNotPresentAndUseMapParts)
	{
		drawLock = new ReentrantLock();
		interactionsLock = new ReentrantLock();
		this.createEditsIfNotPresentAndUseMapParts = createEditsIfNotPresentAndUseMapParts;
		tasksToRunWhenMapReady = new ConcurrentLinkedQueue<>();
		updatesToDraw = new ArrayDeque<>();
	}

	/**
	 * Redraws the entire map, then displays it.
	 */
	public void createAndShowMapFull()
	{
		createAndShowMap(UpdateType.Full, null, null, null, null, null, null);
	}

	public void createAndShowMapFull(Runnable preRun)
	{
		createAndShowMap(UpdateType.Full, null, null, null, null, preRun, null);
	}

	public void createAndShowMapTextChange()
	{
		createAndShowMapTextChange(null);
	}

	public void createAndShowMapTextChange(Runnable postRun)
	{
		createAndShowMap(UpdateType.Text, null, null, null, null, null, postRun);
	}

	public void createAndShowMapFontsChange()
	{
		createAndShowMap(UpdateType.Fonts, null, null, null, null, null, null);
	}

	public void createAndShowMapTerrainChange()
	{
		createAndShowMap(UpdateType.Terrain, null, null, null, null, null, null);
	}

	public void createAndShowMapGrungeOrFrayedEdgeChange()
	{
		createAndShowMap(UpdateType.GrungeAndFray, null, null, null, null, null, null);
	}

	public void createAndShowMapIncrementalUsingCenters(Set<Center> centersChanged)
	{
		createAndShowMap(UpdateType.Incremental, centersChanged, null, null, null, null, null);
	}

	public void createAndShowMapIncrementalUsingCenters(Set<Center> centersChanged, Runnable postRun)
	{
		createAndShowMap(UpdateType.Incremental, centersChanged, null, null, null, null, postRun);
	}

	public void createAndShowMapIncrementalUsingEdges(Set<Edge> edgesChanged)
	{
		createAndShowMap(UpdateType.Incremental, null, edgesChanged, null, null, null, null);
	}

	public void createAndShowMapIncrementalUsingText(List<MapText> textChanged)
	{
		createAndShowMap(UpdateType.Incremental, null, null, textChanged, null, null, null);
	}

	public void createAndShowMapIncrementalUsingText(List<MapText> textChanged, Runnable postRun)
	{
		createAndShowMap(UpdateType.Incremental, null, null, textChanged, null, null, postRun);
	}

	public void createAndShowMapIncrementalUsingIcons(List<FreeIcon> iconsChanged, Runnable postRun)
	{
		createAndShowMap(UpdateType.Incremental, null, null, null, iconsChanged, null, postRun);
	}

	public void reprocessBooks()
	{
		createAndShowMap(UpdateType.ReprocessBooks, null, null, null, null, null, null);
	}

	/**
	 * Redraws the map based on a change that was made.
	 * 
	 * For incremental drawing, this compares the edits in the change with the current state of the edits from getEdits() to determine what
	 * changed.
	 * 
	 * @param change
	 *            The 'before' state. Used to determine what needs to be redrawn.
	 * @param preRun
	 *            Code to run in the foreground thread before drawing this change.
	 */
	public void createAndShowMapFromChange(MapChange change)
	{
		if (change.updateType != UpdateType.Incremental)
		{
			createAndShowMap(change.updateType, null, null, null, null, change.preRun, null);
		}
		else
		{
			Set<Center> centersChanged = getCentersWithChangesInEdits(change.settings.edits);
			Set<Edge> edgesChanged = getEdgesWithChangesInEdits(change.settings.edits);
			List<MapText> textChanged = null;
			List<FreeIcon> iconsChanged = null;
			// createAndShowMap currently does not support an incremental update for both text and centers or edges,
			// so only check for changed text if other changes are empty.
			if (edgesChanged.size() == 0 && centersChanged.size() == 0)
			{
				// See if there was a text change.
				textChanged = getTextWithChangesInEdits(change.settings.edits);
				if (textChanged == null || textChanged.isEmpty())
				{
					// See if there were icon changes.
					iconsChanged = getIconsWithChangesInEdits(change.settings.edits);
				}
			}
			createAndShowMap(UpdateType.Incremental, centersChanged, edgesChanged, textChanged, iconsChanged, change.preRun, null);
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

	private List<MapText> getTextWithChangesInEdits(MapEdits changeEdits)
	{
		// Note this algorithm works because I never delete map texts; I only mark them as empty, which causes them to not draw.
		List<MapText> changed = new ArrayList<>();
		List<MapText> curTextList = getEdits().text;
		for (int i : new Range(curTextList.size()))
		{
			MapText curText = curTextList.get(i);
			if (i > changeEdits.text.size() - 1)
			{
				changed.add(curText);
			}
			else if (!curText.equals(changeEdits.text.get(i)))
			{
				changed.add(curText);
				changed.add(changeEdits.text.get(i));
			}
		}

		if (changeEdits.text.size() > curTextList.size())
		{
			for (int i : new Range(changeEdits.text.size() - curTextList.size()))
			{
				changed.add(changeEdits.text.get(i + curTextList.size()));
			}
		}

		return changed;
	}
	
	private List<FreeIcon> getIconsWithChangesInEdits(MapEdits changeEdits)
	{
		return getEdits().freeIcons.diff(changeEdits.freeIcons);
	}

	/**
	 * Clears values from mapParts as needed to trigger those parts to re-redraw based on what type of update we're making.
	 * 
	 * @param updateType
	 */
	private void clearMapPartsAsNeeded(UpdateType updateType)
	{
		if (mapParts == null)
		{
			return;
		}

		if (updateType == UpdateType.Full)
		{
			if (mapParts != null)
			{
				mapParts = new MapParts();
			}
		}
		else if (updateType == UpdateType.Incremental)
		{

		}
		else if (updateType == UpdateType.Text)
		{

		}
		else if (updateType == UpdateType.Fonts)
		{

		}
		else if (updateType == UpdateType.Terrain)
		{
			mapParts.mapBeforeAddingText = null;
			mapParts.iconDrawer = null;
		}
		else if (updateType == UpdateType.GrungeAndFray)
		{
			mapParts.frayedBorderBlur = null;
			mapParts.frayedBorderColor = null;
			mapParts.frayedBorderMask = null;
			mapParts.grunge = null;
		}
		else if (updateType == UpdateType.ReprocessBooks)
		{

		}
		else
		{
			throw new IllegalStateException("Unrecognized update type: " + updateType);
		}

	}

	/**
	 * Creates a new set that has the most up-to-date version of the centers in the given set. This is necessary because incremental and
	 * full redraws can run out of order, and as a result, a full redraw might recreate the graph, while an incremental change is waiting to
	 * run, causing centersChanged (passed to createAndShowMap) to hold Center objects no longer in the graph, and so they could be out of
	 * date.
	 */
	private Set<Center> getCurrentCenters(Set<Center> centers)
	{
		if (centers == null)
		{
			return null;
		}
		return centers.stream().map(c -> mapParts.graph.centers.get(c.index)).collect(Collectors.toSet());
	}

	/**
	 * Like getCurrentCenters but for edges.
	 */
	private Set<Edge> getCurrentEdges(Set<Edge> edges)
	{
		if (edges == null)
		{
			return null;
		}
		return edges.stream().map(e -> mapParts.graph.edges.get(e.index)).collect(Collectors.toSet());
	}

	private boolean isUpdateTypeThatAllowsInteractions(UpdateType updateType)
	{
		return updateType == UpdateType.Incremental || updateType == UpdateType.Text || updateType == UpdateType.ReprocessBooks;
	}

	private void createAndShowMap(UpdateType updateType, Set<Center> centersChanged, Set<Edge> edgesChanged, List<MapText> textChanged,
			List<FreeIcon> iconsChanged, Runnable preRun, Runnable postRun)
	{
		List<Runnable> preRuns = new ArrayList<>();
		if (preRun != null)
		{
			preRuns.add(preRun);
		}

		List<Runnable> postRuns = new ArrayList<>();
		if (postRun != null)
		{
			postRuns.add(postRun);
		}

		List<MapText> copiedText = textChanged == null ? null
				: textChanged.stream().map(text -> text.deepCopy()).collect(Collectors.toList());
		List<FreeIcon> coppiedIcons = iconsChanged == null ? null
				: iconsChanged.stream().map(icon -> icon.deepCopy()).collect(Collectors.toList());
		innerCreateAndShowMap(updateType, centersChanged, edgesChanged, copiedText, coppiedIcons, preRuns, postRuns);
	}

	/**
	 * Redraws the map, then displays it
	 */
	private void innerCreateAndShowMap(UpdateType updateType, Set<Center> centersChanged, Set<Edge> edgesChanged, List<MapText> textChanged,
			List<FreeIcon> iconsChanged, List<Runnable> preRuns, List<Runnable> postRuns)
	{
		if (!enabled)
		{
			return;
		}

		if (isMapBeingDrawn)
		{
			updatesToDraw.add(new MapUpdate(updateType, centersChanged, edgesChanged, textChanged, iconsChanged, preRuns, postRuns));
			return;
		}

		isMapBeingDrawn = true;
		if (!isUpdateTypeThatAllowsInteractions(updateType))
		{
			isMapReadyForInteractions = false;
		}

		if (updateType != UpdateType.ReprocessBooks)
		{
			onBeginDraw();
		}

		final MapSettings settings = getSettingsFromGUI();

		if (createEditsIfNotPresentAndUseMapParts && settings.edits.isEmpty())
		{
			settings.edits.bakeGeneratedTextAsEdits = true;
		}

		if (preRuns != null)
		{
			for (Runnable runnable : preRuns)
			{
				runnable.run();
			}
		}

		PlatformFactory.getInstance().doInBackgroundThread(new BackgroundTask<UpdateResult>()
		{
			@Override
			public UpdateResult doInBackground() throws IOException, CancelledException
			{
				if (!isUpdateTypeThatAllowsInteractions(updateType))
				{
					Logger.clear();
					interactionsLock.lock();
				}
				drawLock.lock();
				try
				{
					clearMapPartsAsNeeded(updateType);

					if (updateType == UpdateType.Incremental)
					{
						Image map = getCurrentMapForIncrementalUpdate();
						// Incremental update
						if (centersChanged != null && centersChanged.size() > 0 || edgesChanged != null && edgesChanged.size() > 0)
						{
							IntRectangle replaceBounds = new MapCreator().incrementalUpdate(settings, mapParts, map,
									getCurrentCenters(centersChanged), getCurrentEdges(edgesChanged));
							return new UpdateResult(map, replaceBounds, new ArrayList<>());
						}
						else if (textChanged != null && textChanged.size() > 0)
						{
							IntRectangle replaceBounds = new MapCreator().incrementalUpdateText(settings, mapParts, map, textChanged);
							return new UpdateResult(map, replaceBounds, new ArrayList<>());
						}
						else if (iconsChanged != null && iconsChanged.size() > 0)
						{
							IntRectangle replaceBounds = new MapCreator().incrementalUpdateIcons(settings, mapParts, map, iconsChanged);
							return new UpdateResult(map, replaceBounds, new ArrayList<>());
						}
						else
						{
							// Nothing to do.
							return new UpdateResult(map, null, new ArrayList<>());
						}
					}
					else if (updateType == UpdateType.ReprocessBooks)
					{
						if (mapParts != null)
						{
							mapParts.nameCreator = new NameCreator(settings);
						}
						return new UpdateResult(null, null, new ArrayList<>());
					}
					else
					{
						if (maxMapSize != null && (maxMapSize.width <= 0 || maxMapSize.height <= 0))
						{
							return null;
						}

						if (mapParts == null && createEditsIfNotPresentAndUseMapParts)
						{
							mapParts = new MapParts();
						}

						Image map;
						try
						{
							currentNonIncrementalMapCreator = new MapCreator();
							map = currentNonIncrementalMapCreator.createMap(settings, maxMapSize, mapParts);
						}
						catch (CancelledException e)
						{
							Logger.println("Map creation cancelled.");
							return new UpdateResult(null, null, new ArrayList<>());
						}

						System.gc();
						return new UpdateResult(map, null, currentNonIncrementalMapCreator.getWarningMessages());
					}
				}
				finally
				{
					drawLock.unlock();
					if (!isUpdateTypeThatAllowsInteractions(updateType))
					{
						interactionsLock.unlock();
					}
				}
			}

			@Override
			public void done(UpdateResult result)
			{
				Image map = null;
				IntRectangle replaceBounds = null;
				List<String> warningMessages = null;
				if (result != null)
				{
					map = result.map;
					replaceBounds = result.replaceBounds;
					warningMessages = result.warningMessages;
				}

				if (map != null)
				{
					if (createEditsIfNotPresentAndUseMapParts)
					{
						initializeCenterEditsIfEmpty(settings.edits);
						initializeRegionEditsIfEmpty(settings.edits);
						initializeEdgeEditsIfEmpty(settings.edits);
					}

					MapUpdate next = combineAndGetNextUpdateToDraw();

					if (updateType != UpdateType.ReprocessBooks)
					{
						boolean anotherDrawIsQueued = next != null;
						int scaledBorderWidth = settings.drawBorder ? (int) (settings.borderWidth * settings.resolution) : 0;
						onFinishedDrawing(map, anotherDrawIsQueued, scaledBorderWidth,
								replaceBounds == null ? null
										: new Rectangle(replaceBounds.x + scaledBorderWidth, replaceBounds.y + scaledBorderWidth,
												replaceBounds.width, replaceBounds.height),
								warningMessages);
					}

					isMapBeingDrawn = false;

					if (postRuns != null)
					{
						for (Runnable runnable : postRuns)
						{
							runnable.run();
						}
					}

					if (next != null)
					{
						innerCreateAndShowMap(next.updateType, next.centersChanged, next.edgesChanged, next.textChanged, next.iconsChanged, next.preRuns,
								next.postRuns);
					}

					isMapReadyForInteractions = true;
				}
				else
				{
					if (updateType != UpdateType.ReprocessBooks)
					{
						onFailedToDraw();
					}
					isMapBeingDrawn = false;
				}

				while (tasksToRunWhenMapReady.size() > 0)
				{
					tasksToRunWhenMapReady.poll().run();
				}
			}

		});
	}

	private class UpdateResult
	{
		public Image map;
		public IntRectangle replaceBounds;
		public List<String> warningMessages;

		public UpdateResult(Image map, IntRectangle replaceBounds, List<String> warningMessages)
		{
			this.map = map;
			this.replaceBounds = replaceBounds;
			this.warningMessages = warningMessages;
		}
	}

	protected abstract void onBeginDraw();

	public abstract MapSettings getSettingsFromGUI();

	protected abstract void onFinishedDrawing(Image map, boolean anotherDrawIsQueued, int borderWidthAsDrawn,
			Rectangle incrementalChangeArea, List<String> warningMessages);

	protected abstract void onFailedToDraw();

	protected abstract MapEdits getEdits();

	protected abstract Image getCurrentMapForIncrementalUpdate();

	/**
	 * Combines the updates in updatesToDraw when it makes sense to do so, they can be drawn together.
	 * 
	 * @return The combined update to draw
	 */
	private MapUpdate combineAndGetNextUpdateToDraw()
	{
		if (updatesToDraw.isEmpty())
		{
			return null;
		}

		Optional<MapUpdate> full = updatesToDraw.stream().filter(update -> update.updateType == UpdateType.Full).findFirst();
		if (full.isPresent())
		{
			// There's a full update on the queue. We only need to do that one.
			updatesToDraw.clear();
			return full.get();
		}

		// Combine other types updates until we hit one that isn't
		// the same type.
		MapUpdate update = updatesToDraw.poll();
		while (updatesToDraw.size() > 0 && updatesToDraw.peek().updateType == update.updateType)
		{
			update.add(updatesToDraw.poll());
		}
		return update;
	}

	private void initializeCenterEditsIfEmpty(MapEdits edits)
	{
		if (edits.centerEdits.isEmpty())
		{
			edits.initializeCenterEdits(mapParts.graph.centers, mapParts.iconDrawer);
		}
	}

	private void initializeEdgeEditsIfEmpty(MapEdits edits)
	{
		if (edits.edgeEdits.isEmpty())
		{
			edits.initializeEdgeEdits(mapParts.graph.edges);
		}
	}

	private void initializeRegionEditsIfEmpty(MapEdits edits)
	{
		if (edits.regionEdits.isEmpty())
		{
			edits.initializeRegionEdits(mapParts.graph.regions.values());
		}
	}

	public void setMaxMapSize(Dimension dimension)
	{
		maxMapSize = dimension;
	}

	private class MapUpdate
	{
		Set<Center> centersChanged;
		Set<Edge> edgesChanged;
		List<MapText> textChanged;
		List<FreeIcon> iconsChanged;
		UpdateType updateType;
		List<Runnable> postRuns;
		List<Runnable> preRuns;

		public MapUpdate(UpdateType updateType, Set<Center> centersChanged, Set<Edge> edgesChanged, List<MapText> textChanged,
				List<FreeIcon> iconsChanged, List<Runnable> preRuns, List<Runnable> postRuns)
		{
			this.updateType = updateType;
			if (centersChanged != null)
			{
				this.centersChanged = new HashSet<Center>(centersChanged);
			}
			if (edgesChanged != null)
			{
				this.edgesChanged = new HashSet<Edge>(edgesChanged);
			}
			if (textChanged != null)
			{
				this.textChanged = new ArrayList<>(textChanged);
			}
			if (iconsChanged != null)
			{
				this.iconsChanged = new ArrayList<>(iconsChanged);
			}

			if (postRuns != null)
			{
				this.postRuns = postRuns;
			}
			else
			{
				this.postRuns = new ArrayList<>();
			}

			if (preRuns != null)
			{
				this.preRuns = preRuns;
			}
			else
			{
				this.preRuns = new ArrayList<>();
			}
		}

		public void add(MapUpdate other)
		{
			if (other == null)
			{
				return;
			}

			if (updateType != other.updateType)
			{
				throw new IllegalArgumentException();
			}

			preRuns.addAll(other.preRuns);
			postRuns.addAll(other.postRuns);

			if (updateType == UpdateType.Incremental)
			{
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

				if (textChanged != null && other.textChanged != null)
				{
					textChanged.addAll(other.textChanged);
				}
				else if (textChanged == null && other.textChanged != null)
				{
					textChanged = new ArrayList<>(other.textChanged);
				}
				
				if (iconsChanged != null && other.iconsChanged != null)
				{
					iconsChanged.addAll(other.iconsChanged);
				}
				else if (iconsChanged == null && other.iconsChanged != null)
				{
					iconsChanged = new ArrayList<>(iconsChanged);
				}
			}
		}
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	public void doIfMapIsReadyForInteractions(Runnable action)
	{
		doIfMapIsReadyForInteractions(action, true);
	}

	public void doWhenMapIsReadyForInteractions(Runnable action)
	{
		doIfMapIsReadyForInteractions(action, false);
	}

	private void doIfMapIsReadyForInteractions(Runnable action, boolean skipIfLocked)
	{
		// One might wonder why I have both a boolean flag
		// (isMapReadyForInteractions) and a lock (interactionsLock) to prevent
		// user interactions while a map is doing a non-incremental draw. The
		// reason for the flag is to prevent new user interactions
		// after a draw is started and before the draw has finished (since some
		// of the drawing is done in the event dispatch thread
		// after the map finishes drawing in a swing worker thread). The lock is
		// needed to prevent new swing worker threads from starting
		// drawing a map while the event dispatch thread is still handling a
		// user interaction (since doing so could result in something like
		// mapParts.graph being null, which would cause a crash).
		if (isMapReadyForInteractions)
		{
			boolean isLocked = false;
			try
			{
				isLocked = interactionsLock.tryLock(0, TimeUnit.MILLISECONDS);
				if (isLocked)
				{
					action.run();
				}
				else
				{
					if (skipIfLocked)
					{
						return;
					}
					else
					{
						tasksToRunWhenMapReady.add(action);
					}
				}
			}
			catch (InterruptedException e1)
			{
			}
			finally
			{
				if (isLocked)
				{
					interactionsLock.unlock();
				}
			}
		}
		else
		{
			if (!skipIfLocked)
			{
				tasksToRunWhenMapReady.add(action);
			}
		}
	}

	public void dowWhenMapIsNotDrawing(Runnable action)
	{
		if (!isMapBeingDrawn)
		{
			boolean isLocked = false;
			try
			{
				isLocked = drawLock.tryLock(0, TimeUnit.MILLISECONDS);
				if (isLocked)
				{
					action.run();
				}
				else
				{
					tasksToRunWhenMapReady.add(action);
				}
			}
			catch (InterruptedException e1)
			{
			}
			finally
			{
				if (isLocked)
				{
					drawLock.unlock();
				}
			}
		}
		else
		{
			tasksToRunWhenMapReady.add(action);
		}
	}

	public boolean isMapBeingDrawn()
	{
		return isMapBeingDrawn;
	}

	public void cancel()
	{
		if (currentNonIncrementalMapCreator != null && isMapBeingDrawn)
		{
			currentNonIncrementalMapCreator.cancel();
		}
		tasksToRunWhenMapReady.clear();
	}

}
