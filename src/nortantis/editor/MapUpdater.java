package nortantis.editor;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import nortantis.BorderPosition;
import nortantis.CancelledException;
import nortantis.DebugFlags;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.NameCreator;
import nortantis.Stopwatch;
import nortantis.geom.Dimension;
import nortantis.geom.IntRectangle;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.BackgroundTask;
import nortantis.platform.Image;
import nortantis.platform.PlatformFactory;
import nortantis.swing.MapEdits;
import nortantis.swing.UpdateType;
import nortantis.util.Helper;
import nortantis.util.Logger;

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
	private ArrayDeque<MapUpdate> nonIncrementalUpdatesToDraw;
	private ArrayDeque<MapUpdate> incrementalUpdatesToDraw;
	private ArrayDeque<MapUpdate> lowPriorityUpdatesToDraw;
	private MapCreator currentMapCreator;
	private MapUpdate currentUpdate;
	private ConcurrentHashMap<Integer, Center> centersToRedrawLowPriority;


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
		nonIncrementalUpdatesToDraw = new ArrayDeque<>();
		incrementalUpdatesToDraw = new ArrayDeque<>();
		lowPriorityUpdatesToDraw = new ArrayDeque<>();
		centersToRedrawLowPriority = new ConcurrentHashMap<>();
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

	public void createAndShowMapTerrainChange(Runnable preRun)
	{
		createAndShowMap(UpdateType.Terrain, null, null, null, null, preRun, null);
	}

	public void createAndShowMapGrungeOrFrayedEdgeChange()
	{
		createAndShowMap(UpdateType.GrungeAndFray, null, null, null, null, null, null);
	}

	public void createAndShowMapOverlayImage()
	{
		createAndShowMap(UpdateType.OverlayImage, null, null, null, null, null, null);
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

	public void createAndShowMapIncrementalUsingIcons(List<FreeIcon> iconsChanged)
	{
		createAndShowMap(UpdateType.Incremental, null, null, null, iconsChanged, null, null);
	}

	public void createAndShowLowPriorityChanges()
	{
		if (!centersToRedrawLowPriority.isEmpty())
		{
			Set<Integer> centersToDrawIds = new HashSet<>(centersToRedrawLowPriority.keySet());
			centersToRedrawLowPriority.clear();
			innerCreateAndShowMap(UpdateType.Incremental, centersToDrawIds, null, null, null, null, null, true);
		}
	}

	public void addRoadsToRedrawLowPriority(List<Road> roads, double resolutionScale)
	{
		if (mapParts != null && mapParts.graph != null)
		{
			for (Road road : roads)
			{
				if (road == null)
				{
					continue;
				}

				for (Point point : road.path)
				{
					Center center = mapParts.graph.findClosestCenter(point.mult(resolutionScale), true);
					if (center != null)
					{
						centersToRedrawLowPriority.put(center.index, center);
					}
				}
			}
		}
		else
		{
			assert false;
		}
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
			Set<Integer> centersChanged = getIdsOfCentersWithChangesInEdits(change.settings.edits);
			Set<Integer> edgesChanged = getIdsOfEdgesWithChangesInEdits(change.settings.edits);
			Collection<MapText> textChanged = getTextWithChangesInEdits(change.settings.edits);
			List<FreeIcon> iconsChanged = getIconsWithChangesInEdits(change.settings.edits);
			// I haven't bothered to add roads to the changes that can be passed in to createAndShowMapUsingIds, so I'm just passing in the
			// id's of centers under those roads. The downside to doing this is that it will do a little extra drawing.
			centersChanged.addAll(getCentersIdsOfRoadsChanged(change.settings.edits, getSettingsFromGUI().resolution));
			createAndShowMapUsingIds(UpdateType.Incremental, centersChanged, edgesChanged, textChanged, iconsChanged, change.preRun, null);
		}
	}

	private Collection<Integer> getCentersIdsOfRoadsChanged(MapEdits edits, double resolutionScale)
	{
		Set<List<Point>> changePaths = edits.roads.stream().map(road -> road.path).collect(Collectors.toSet());
		Set<List<Point>> currentPaths = getEdits().roads.stream().map(road -> road.path).collect(Collectors.toSet());
		Set<List<Point>> diff = Helper.getElementsNotInIntersection(changePaths, currentPaths);
		Set<Integer> diffCenterIds = getIdsOfCentersPointsAreOn(pointListsToPointSet(diff), resolutionScale);
		return diffCenterIds;
	}

	public static Set<Point> pointListsToPointSet(Set<List<Point>> listSet)
	{
		Set<Point> result = new HashSet<>();
		for (List<Point> list : listSet)
		{
			for (Point p : list)
			{
				result.add(p);
			}
		}
		return result;
	}


	private Set<Integer> getIdsOfCentersPointsAreOn(Set<Point> pointsResolutionInvariant, double resolutionScale)
	{
		Set<Integer> result = new HashSet<>();
		for (Point point : pointsResolutionInvariant)
		{
			Center center = mapParts.graph.findClosestCenter(point.mult(resolutionScale), true);
			if (center != null)
			{
				centersToRedrawLowPriority.put(center.index, center);
			}
		}

		return result;
	}

	private Set<Integer> getIdsOfCentersWithChangesInEdits(MapEdits changeEdits)
	{
		Set<Integer> changedCentersIds = getEdits().centerEdits.values().stream()
				.filter(cEdit -> !cEdit.equals(changeEdits.centerEdits.get(cEdit.index))).map(cEdit -> cEdit.index)
				.collect(Collectors.toSet());

		Set<RegionEdit> regionChanges = getEdits().regionEdits.values().stream()
				.filter(rEdit -> !rEdit.equals(changeEdits.regionEdits.get(rEdit.regionId))).collect(Collectors.toSet());
		for (RegionEdit rEdit : regionChanges)
		{
			Set<Integer> regionCenterEdits = changeEdits.centerEdits.values().stream()
					.filter(cEdit -> cEdit.regionId != null && cEdit.regionId == rEdit.regionId).map(cEdit -> cEdit.index)
					.collect(Collectors.toSet());
			changedCentersIds.addAll(regionCenterEdits);
		}

		return changedCentersIds;
	}

	private Set<Integer> getIdsOfEdgesWithChangesInEdits(MapEdits changeEdits)
	{
		return getEdits().edgeEdits.stream().filter(eEdit -> !eEdit.equals(changeEdits.edgeEdits.get(eEdit.index)))
				.map(eEdit -> eEdit.index).collect(Collectors.toSet());
	}

	private Collection<MapText> getTextWithChangesInEdits(MapEdits changeEdits)
	{
		Set<MapText> fromEdits = new HashSet<>(changeEdits.text);
		Set<MapText> curText = new HashSet<>(getEdits().text);
		Collection<MapText> result = Helper.getElementsNotInIntersection(fromEdits, curText);
		return result;
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
		else if (updateType == UpdateType.OverlayImage)
		{

		}
		else if (updateType == UpdateType.NoDraw)
		{

		}
		else
		{
			throw new IllegalStateException("Unrecognized update type: " + updateType);
		}

	}

	private boolean isUpdateTypeThatAllowsInteractions(UpdateType updateType)
	{
		return updateType == UpdateType.Incremental || updateType == UpdateType.Text || updateType == UpdateType.ReprocessBooks
				|| updateType == UpdateType.NoDraw;
	}

	private void createAndShowMap(UpdateType updateType, Set<Center> centersChanged, Set<Edge> edgesChanged, List<MapText> textChanged,
			List<FreeIcon> iconsChanged, Runnable preRun, Runnable postRun)
	{
		Set<Integer> centersChangedIds = centersChanged == null ? null
				: centersChanged.stream().map(c -> c.index).collect(Collectors.toSet());
		Set<Integer> edgesChangedIds = edgesChanged == null ? null : edgesChanged.stream().map(e -> e.index).collect(Collectors.toSet());

		createAndShowMapUsingIds(updateType, centersChangedIds, edgesChangedIds, textChanged, iconsChanged, preRun, postRun);
	}

	private void createAndShowMapUsingIds(UpdateType updateType, Set<Integer> centersChangedIds, Set<Integer> edgesChangedIds,
			Collection<MapText> textChanged, List<FreeIcon> iconsChanged, Runnable preRun, Runnable postRun)
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
		innerCreateAndShowMap(updateType, centersChangedIds, edgesChangedIds, copiedText, iconsChanged, preRuns, postRuns, false);
	}

	/**
	 * Redraws the map, then displays it
	 */
	private void innerCreateAndShowMap(UpdateType updateType, Set<Integer> centersChangedIds, Set<Integer> edgesChangedIds,
			List<MapText> textChanged, List<FreeIcon> iconsChanged, List<Runnable> preRuns, List<Runnable> postRuns,
			boolean isLowPriorityChange)
	{
		if (!enabled)
		{
			return;
		}

		// Low-priority updates only support incremental updates.
		assert !isLowPriorityChange || updateType == UpdateType.Incremental;

		if (updateType == UpdateType.NoDraw)
		{
			return;
		}

		onDrawSubmitted(updateType);

		if (isMapBeingDrawn)
		{
			if (!isLowPriorityChange && currentMapCreator != null && currentUpdate != null && currentUpdate.isLowPriority)
			{
				// Cancel the low priority change, then add the new update before it on the queue.
				currentMapCreator.cancel();
				if (updateType == UpdateType.Incremental)
				{
					incrementalUpdatesToDraw.add(new MapUpdate(updateType, centersChangedIds, edgesChangedIds, textChanged, iconsChanged,
							preRuns, postRuns, isLowPriorityChange));
				}
				else
				{
					nonIncrementalUpdatesToDraw.add(new MapUpdate(updateType, centersChangedIds, edgesChangedIds, textChanged, iconsChanged,
							preRuns, postRuns, isLowPriorityChange));
				}

				lowPriorityUpdatesToDraw.add(currentUpdate);
				return;
			}
			else
			{
				if (isLowPriorityChange)
				{
					lowPriorityUpdatesToDraw.add(new MapUpdate(updateType, centersChangedIds, edgesChangedIds, textChanged, iconsChanged,
							preRuns, postRuns, isLowPriorityChange));
				}
				else
				{
					if (updateType == UpdateType.Incremental)
					{
						incrementalUpdatesToDraw.add(new MapUpdate(updateType, centersChangedIds, edgesChangedIds, textChanged,
								iconsChanged, preRuns, postRuns, isLowPriorityChange));
					}
					else
					{
						nonIncrementalUpdatesToDraw.add(new MapUpdate(updateType, centersChangedIds, edgesChangedIds, textChanged,
								iconsChanged, preRuns, postRuns, isLowPriorityChange));
					}
				}
				return;
			}
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

		if (createEditsIfNotPresentAndUseMapParts && !settings.edits.isInitialized())
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
			public UpdateResult doInBackground() throws IOException
			{
				if (!isUpdateTypeThatAllowsInteractions(updateType))
				{
					Logger.clear();
					interactionsLock.lock();
				}
				drawLock.lock();

				try
				{
					try
					{
						currentUpdate = new MapUpdate(updateType, centersChangedIds, edgesChangedIds, textChanged, iconsChanged, preRuns,
								postRuns, isLowPriorityChange);

						clearMapPartsAsNeeded(updateType);

						if (updateType == UpdateType.Incremental)
						{
							Image map = getCurrentMapForIncrementalUpdate();
							IntRectangle combinedReplaceBounds = null;
							// Incremental update
							if (centersChangedIds != null && centersChangedIds.size() > 0
									|| edgesChangedIds != null && edgesChangedIds.size() > 0)
							{
								Stopwatch incrementalUpdateTimer = new Stopwatch("do incremental update for centers and edges");
								currentMapCreator = new MapCreator();
								IntRectangle replaceBounds = currentMapCreator.incrementalUpdateForCentersAndEdges(settings, mapParts, map,
										centersChangedIds, edgesChangedIds, isLowPriorityChange);
								combinedReplaceBounds = combinedReplaceBounds == null ? replaceBounds
										: combinedReplaceBounds.add(replaceBounds);
								if (DebugFlags.printIncrementalUpdateTimes())
								{
									incrementalUpdateTimer.printElapsedTime();
								}
							}

							if (textChanged != null && textChanged.size() > 0)
							{
								Stopwatch incrementalUpdateTimer = new Stopwatch("do incremental update for text");
								currentMapCreator = new MapCreator();
								IntRectangle replaceBounds = currentMapCreator.incrementalUpdateText(settings, mapParts, map, textChanged);
								combinedReplaceBounds = combinedReplaceBounds == null ? replaceBounds
										: combinedReplaceBounds.add(replaceBounds);
								if (DebugFlags.printIncrementalUpdateTimes())
								{
									incrementalUpdateTimer.printElapsedTime();
								}
							}

							if (iconsChanged != null && iconsChanged.size() > 0)
							{
								Stopwatch incrementalUpdateTimer = new Stopwatch("do incremental update for icons");
								currentMapCreator = new MapCreator();
								IntRectangle replaceBounds = currentMapCreator.incrementalUpdateIcons(settings, mapParts, map,
										iconsChanged);
								combinedReplaceBounds = combinedReplaceBounds == null ? replaceBounds
										: combinedReplaceBounds.add(replaceBounds);
								if (DebugFlags.printIncrementalUpdateTimes())
								{
									incrementalUpdateTimer.printElapsedTime();
								}
							}

							return new UpdateResult(map, combinedReplaceBounds, new ArrayList<>());
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
							return fullDraw(settings);
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
				catch (CancelledException ex)
				{
					return new UpdateResult(null, null, new ArrayList<>());
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

					if (currentMapCreator != null)
					{
						addLowPriorityCentersToRedraw(currentMapCreator.centersToRedrawLowPriority);
					}

					currentMapCreator = null;
					currentUpdate = null;

					MapUpdate next = combineAndGetNextUpdateToDraw();

					if (updateType != UpdateType.ReprocessBooks)
					{
						boolean anotherDrawIsQueued = next != null;
						int scaledBorderWidth = settings.drawBorder && settings.borderPosition == BorderPosition.Outside_map
								? (int) (settings.borderWidth * settings.resolution)
								: 0;
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
						innerCreateAndShowMap(next.updateType, next.centersChangedIds, next.edgesChangedIds, next.textChanged,
								next.iconsChanged, next.preRuns, next.postRuns, next.isLowPriority);
					}
					else
					{
						isMapReadyForInteractions = true;

						while (tasksToRunWhenMapReady.size() > 0)
						{
							tasksToRunWhenMapReady.poll().run();
						}
					}
				}
				else
				{
					boolean isCanceled = currentMapCreator != null ? currentMapCreator.isCanceled() : false;

					if (updateType != UpdateType.ReprocessBooks && !isCanceled)
					{
						onFailedToDraw();
					}
					currentMapCreator = null;
					currentUpdate = null;
					isMapBeingDrawn = false;

					if (isCanceled)
					{
						MapUpdate next = combineAndGetNextUpdateToDraw();
						if (next != null)
						{
							innerCreateAndShowMap(next.updateType, next.centersChangedIds, next.edgesChangedIds, next.textChanged,
									next.iconsChanged, next.preRuns, next.postRuns, next.isLowPriority);
						}
						else
						{
							while (tasksToRunWhenMapReady.size() > 0)
							{
								tasksToRunWhenMapReady.poll().run();
							}
						}
					}
				}

			}

		});
	}

	private UpdateResult fullDraw(MapSettings settings)
	{
		if (maxMapSize != null && (maxMapSize.width <= 0 || maxMapSize.height <= 0))
		{
			return null;
		}

		if (mapParts == null && createEditsIfNotPresentAndUseMapParts)
		{
			mapParts = new MapParts();
		}

		centersToRedrawLowPriority.clear();

		Image map;
		try
		{
			currentMapCreator = new MapCreator();
			map = currentMapCreator.createMap(settings, maxMapSize, mapParts);
		}
		catch (CancelledException e)
		{
			Logger.println("Map creation cancelled.");
			return new UpdateResult(null, null, new ArrayList<>());
		}

		System.gc();
		return new UpdateResult(map, null, currentMapCreator.getWarningMessages());
	}

	private void addLowPriorityCentersToRedraw(Map<Integer, Center> toAdd)
	{
		for (Center c : toAdd.values())
		{
			centersToRedrawLowPriority.put(c.index, c);
		}
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

	protected void onDrawSubmitted(UpdateType updateType)
	{
	}

	public abstract MapSettings getSettingsFromGUI();

	protected abstract void onFinishedDrawing(Image map, boolean anotherDrawIsQueued, int borderPaddingAsDrawn,
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
		if (nonIncrementalUpdatesToDraw.isEmpty() && incrementalUpdatesToDraw.isEmpty() && lowPriorityUpdatesToDraw.isEmpty())
		{
			return null;
		}

		Optional<MapUpdate> full = nonIncrementalUpdatesToDraw.stream().filter(update -> update.updateType == UpdateType.Full).findFirst();
		if (full.isPresent())
		{
			// There's a full update on the queue. We only need to do that one.
			nonIncrementalUpdatesToDraw.clear();
			incrementalUpdatesToDraw.clear();
			lowPriorityUpdatesToDraw.clear();
			return full.get();
		}

		// Always process non-incremental changes before incremental changes because the drawing code for incremental changes assumes
		// mapParts is properly populated for the current settings in the GUI, which isn't always true if multiple changes are queued and
		// you process them in the order received. Doing so could cause a crash, especially when undoing and redoing a bunch of changes that
		// include incremental and non-incremental, but not full.
		if (!nonIncrementalUpdatesToDraw.isEmpty())
		{
			// Combine other types updates until we hit one that isn't
			// the same type.
			MapUpdate update = nonIncrementalUpdatesToDraw.poll();
			while (nonIncrementalUpdatesToDraw.size() > 0 && nonIncrementalUpdatesToDraw.peek().updateType == update.updateType)
			{
				update.add(nonIncrementalUpdatesToDraw.poll());
			}
			return update;
		}

		if (!incrementalUpdatesToDraw.isEmpty())
		{
			// Combine them all
			MapUpdate update = incrementalUpdatesToDraw.poll();
			while (incrementalUpdatesToDraw.size() > 0)
			{
				update.add(incrementalUpdatesToDraw.poll());
			}
			return update;
		}

		if (!lowPriorityUpdatesToDraw.isEmpty())
		{
			// Combine other types updates until we hit one that isn't
			// the same type.
			MapUpdate update = lowPriorityUpdatesToDraw.poll();
			while (lowPriorityUpdatesToDraw.size() > 0 && lowPriorityUpdatesToDraw.peek().updateType == update.updateType)
			{
				update.add(lowPriorityUpdatesToDraw.poll());
			}
			return update;
		}

		return null;
	}

	private void initializeCenterEditsIfEmpty(MapEdits edits)
	{
		if (edits.centerEdits.isEmpty())
		{
			edits.initializeCenterEdits(mapParts.graph.centers);
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
		Set<Integer> centersChangedIds;
		Set<Integer> edgesChangedIds;
		List<MapText> textChanged;
		List<FreeIcon> iconsChanged;
		UpdateType updateType;
		List<Runnable> postRuns;
		List<Runnable> preRuns;
		boolean isLowPriority;


		public MapUpdate(UpdateType updateType, Set<Integer> centersChangedIds, Set<Integer> edgesChangedIds, List<MapText> textChanged,
				List<FreeIcon> iconsChanged, List<Runnable> preRuns, List<Runnable> postRuns, boolean isLowPriority)
		{
			this.updateType = updateType;
			if (centersChangedIds != null)
			{
				this.centersChangedIds = new HashSet<>(centersChangedIds);
			}
			if (edgesChangedIds != null)
			{
				this.edgesChangedIds = new HashSet<>(edgesChangedIds);
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

			this.isLowPriority = isLowPriority;
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
				if (centersChangedIds != null && other.centersChangedIds != null)
				{
					centersChangedIds.addAll(other.centersChangedIds);
				}
				else if (centersChangedIds == null && other.centersChangedIds != null)
				{
					centersChangedIds = new HashSet<>(other.centersChangedIds);
				}

				if (edgesChangedIds != null && other.edgesChangedIds != null)
				{
					edgesChangedIds.addAll(other.edgesChangedIds);
				}
				else if (edgesChangedIds == null && other.edgesChangedIds != null)
				{
					edgesChangedIds = new HashSet<>(other.edgesChangedIds);
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
					iconsChanged = new ArrayList<>(other.iconsChanged);
				}
			}

			isLowPriority = isLowPriority && other.isLowPriority;
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
		MapCreator current = currentMapCreator;
		if (current != null && isMapBeingDrawn)
		{
			current.cancel();
		}
		tasksToRunWhenMapReady.clear();
		centersToRedrawLowPriority.clear();
		isMapReadyForInteractions = false;
	}

}
