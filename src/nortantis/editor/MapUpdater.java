package nortantis.editor;

import nortantis.*;
import nortantis.geom.Dimension;
import nortantis.geom.IntRectangle;
import nortantis.geom.Point;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.BackgroundTask;
import nortantis.platform.Image;
import nortantis.platform.PlatformFactory;
import nortantis.swing.MapEdits;
import nortantis.swing.SwingHelper;
import nortantis.swing.UpdateType;
import nortantis.util.Helper;
import nortantis.util.Logger;
import nortantis.util.Stopwatch;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public abstract class MapUpdater
{
	private boolean isMapBeingDrawn;
	private ReentrantLock drawLock;
	private ReentrantLock interactionsLock;
	/**
	 * Guards the shared live map buffer returned by {@link #getCurrentMapForIncrementalUpdate()} against a torn read. An incremental
	 * update takes the write lock only for the brief final blit that writes its finished snippet into the buffer (via
	 * {@link MapCreator#setIncrementalMapWriteLock}), NOT for the expensive snippet computation - so that computation runs in parallel
	 * with an in-flight display rescale. Display code that reads the buffer's pixels off the EDT (e.g. a background rescale) holds the
	 * read lock via {@link #getMapReadLock()} while it does so. Full draws build a brand new {@link Image} instead of mutating the live
	 * one, so they don't need this lock.
	 */
	private final ReadWriteLock mapBufferLock = new ReentrantReadWriteLock();
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

	public void createAndShowMapGrungeOrFrayedEdgeChange()
	{
		createAndShowMap(UpdateType.GrungeAndFray, null, null, null, null, null, null);
	}

	public void createAndShowMapOverlayImageChange()
	{
		createAndShowMap(UpdateType.OverlayImage, null, null, null, null, null, null);
	}

	public void createAndShowMapGridOverlayChange()
	{
		createAndShowMap(UpdateType.GridOverlay, null, null, null, null, null, null);
	}

	public void createAndShowMapIncrementalUsingCenters(Set<Center> centersChanged)
	{
		createAndShowMap(UpdateType.Incremental, centersChanged, null, null, null, null, null);
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

	/**
	 * Redraws the centers that earlier draws deferred as low priority. {@code isUndoRedo} should be true when this deferred pass is part of
	 * an undo or redo (the Undoer schedules it after the main undo/redo draw) and false when it follows a forward edit, so the resulting
	 * draw is tagged truthfully.
	 */
	public void createAndShowLowPriorityChanges(boolean isUndoRedo)
	{
		if (!centersToRedrawLowPriority.isEmpty())
		{
			Set<Integer> centersToDrawIds = new HashSet<>(centersToRedrawLowPriority.keySet());
			centersToRedrawLowPriority.clear();
			innerCreateAndShowMap(UpdateType.Incremental, centersToDrawIds, null, null, null, null, null, true, isUndoRedo);
		}
	}

	/**
	 * Queues every center under every node of {@code roads} for a later low-priority redraw. Used after a road edit (add, extend, or erase)
	 * to make sure the road is re-rendered along its full length, not just inside the eager incremental update bounds.
	 *
	 * <p>
	 * Why a full-length redraw is necessary: depending on settings, roads are drawn with a non-solid stroke (dashes, dots, etc.). The dash
	 * pattern is laid out along the entire road, so editing one part of a road can shift the dash phase along the rest of it. A tight eager
	 * incremental redraw is enough to fix curve-shape changes at the cut (see
	 * {@link nortantis.PathOperations#findInnerNeighborsOfCutEndpoints}), but it won't update the dash phase on the parts of the road that
	 * fall outside those bounds. The low-priority pass picks those up.
	 *
	 * <p>
	 * The pass is "low priority" because the dash-phase shift is subtle compared to changes to the road's shape, and deferring it keeps the
	 * editor responsive during drags.
	 */
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

				for (RoadPathNode node : road.nodes)
				{
					Center center = mapParts.graph.findClosestCenter(node.getLoc().mult(resolutionScale), true);
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
	 */
	/**
	 * Redraws the map for an undo or redo. {@code isUndoRedo} is carried with the resulting draw (see {@link MapUpdate#isUndoRedo}) so its
	 * completion can be recognized when the draw finishes — used to avoid warning about cities removed for water during a corrective
	 * undo/redo draw rather than a forward change.
	 */
	public void createAndShowMapFromChange(MapChange change, boolean isUndoRedo)
	{
		if (change.updateType != UpdateType.Incremental)
		{
			createAndShowMap(change.updateType, null, null, null, null, change.preRun, null, isUndoRedo);
		}
		else
		{
			Set<Integer> centersChanged = getIdsOfCentersWithChangesInEdits(change.settings.edits);
			Collection<MapText> textChanged = getTextWithChangesInEdits(change.settings.edits);
			List<FreeIcon> iconsChanged = getIconsWithChangesInEdits(change.settings.edits);
			// I haven't bothered to add roads to the changes that can be passed in to createAndShowMapUsingIds, so I'm just passing in the
			// id's of centers under those roads. The downside to doing this is that it will do a little extra drawing.
			centersChanged.addAll(getCentersIdsOfRoadsChanged(change.settings.edits, getSettingsFromGUI().resolution));
			centersChanged.addAll(getCentersIdsOfRiversChanged(change.settings.edits, getSettingsFromGUI().resolution));
			createAndShowMapUsingIds(UpdateType.Incremental, centersChanged, null, textChanged, iconsChanged, change.preRun, null, isUndoRedo);
		}
	}

	private Collection<Integer> getCentersIdsOfRoadsChanged(MapEdits edits, double resolutionScale)
	{
		Set<List<Point>> changePaths = edits.roads.stream().map(road -> PathOperations.toLocationList(road.nodes)).collect(Collectors.toSet());
		Set<List<Point>> currentPaths = getEdits().roads.stream().map(road -> PathOperations.toLocationList(road.nodes)).collect(Collectors.toSet());
		Set<List<Point>> diff = Helper.getElementsNotInIntersection(changePaths, currentPaths);
		Set<Integer> diffCenterIds = getIdsOfCentersPointsAreOn(pointListsToPointSet(diff), resolutionScale);
		return diffCenterIds;
	}

	private Collection<Integer> getCentersIdsOfRiversChanged(MapEdits edits, double resolutionScale)
	{
		// Use the full RiverPathNode lists (not just locations) so that width-only or seed-only
		// changes — e.g. drawing a different-width river over an existing one — are still detected
		// as differences and the affected centers get redrawn.
		Set<List<RiverPathNode>> changeNodes = edits.rivers.stream().map(river -> (List<RiverPathNode>) new ArrayList<>(river.nodes)).collect(Collectors.toSet());
		Set<List<RiverPathNode>> currentNodes = getEdits().rivers.stream().map(river -> (List<RiverPathNode>) new ArrayList<>(river.nodes)).collect(Collectors.toSet());
		Set<List<RiverPathNode>> diff = Helper.getElementsNotInIntersection(changeNodes, currentNodes);
		Set<Integer> result = new HashSet<>();
		for (List<RiverPathNode> nodes : diff)
		{
			for (RiverPathNode node : nodes)
			{
				Center center = mapParts.graph.findClosestCenter(node.getLoc().mult(resolutionScale), true);
				if (center != null)
				{
					result.add(center.index);
				}
			}
		}
		return result;
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
		Set<Integer> changedCentersIds = getEdits().centerEdits.values().stream().filter(cEdit -> !cEdit.equals(changeEdits.centerEdits.get(cEdit.index))).map(cEdit -> cEdit.index)
				.collect(Collectors.toSet());

		Set<RegionEdit> regionChanges = getEdits().regionEdits.values().stream().filter(rEdit -> !rEdit.equals(changeEdits.regionEdits.get(rEdit.regionId))).collect(Collectors.toSet());
		for (RegionEdit rEdit : regionChanges)
		{
			Set<Integer> regionCenterEdits = changeEdits.centerEdits.values().stream().filter(cEdit -> cEdit.regionId != null && cEdit.regionId == rEdit.regionId).map(cEdit -> cEdit.index)
					.collect(Collectors.toSet());
			changedCentersIds.addAll(regionCenterEdits);
		}

		return changedCentersIds;
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
				mapParts.closeImages();
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
			if (mapParts.mapBeforeAddingText != null)
			{
				mapParts.mapBeforeAddingText.close();
			}
			mapParts.mapBeforeAddingText = null;
		}
		else if (updateType == UpdateType.GrungeAndFray)
		{
			if (mapParts.frayedBorderBlur != null)
			{
				mapParts.frayedBorderBlur.close();
			}
			mapParts.frayedBorderBlur = null;
			if (mapParts.frayedBorderMask != null)
			{
				mapParts.frayedBorderMask.close();
			}
			mapParts.frayedBorderMask = null;
			if (mapParts.grunge != null)
			{
				mapParts.grunge.close();
			}
			mapParts.grunge = null;
		}
		else if (updateType == UpdateType.ReprocessBooks)
		{

		}
		else if (updateType == UpdateType.OverlayImage)
		{

		}
		else if (updateType == UpdateType.GridOverlay)
		{
			if (mapParts.mapBeforeAddingText != null)
			{
				mapParts.mapBeforeAddingText.close();
			}
			mapParts.mapBeforeAddingText = null;
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
		return updateType == UpdateType.Incremental || updateType == UpdateType.Text || updateType == UpdateType.ReprocessBooks || updateType == UpdateType.NoDraw;
	}

	private void createAndShowMap(UpdateType updateType, Set<Center> centersChanged, Set<Edge> edgesChanged, List<MapText> textChanged, List<FreeIcon> iconsChanged, Runnable preRun, Runnable postRun)
	{
		createAndShowMap(updateType, centersChanged, edgesChanged, textChanged, iconsChanged, preRun, postRun, false);
	}

	private void createAndShowMap(UpdateType updateType, Set<Center> centersChanged, Set<Edge> edgesChanged, List<MapText> textChanged, List<FreeIcon> iconsChanged, Runnable preRun, Runnable postRun,
			boolean isUndoRedo)
	{
		Set<Integer> centersChangedIds = centersChanged == null ? null : centersChanged.stream().map(c -> c.index).collect(Collectors.toSet());
		Set<Integer> edgesChangedIds = edgesChanged == null ? null : edgesChanged.stream().map(e -> e.index).collect(Collectors.toSet());

		createAndShowMapUsingIds(updateType, centersChangedIds, edgesChangedIds, textChanged, iconsChanged, preRun, postRun, isUndoRedo);
	}

	private void createAndShowMapUsingIds(UpdateType updateType, Set<Integer> centersChangedIds, Set<Integer> edgesChangedIds, Collection<MapText> textChanged, List<FreeIcon> iconsChanged,
			Runnable preRun, Runnable postRun, boolean isUndoRedo)
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

		List<MapText> copiedText = textChanged == null ? null : textChanged.stream().map(text -> text.deepCopy()).collect(Collectors.toList());
		innerCreateAndShowMap(updateType, centersChangedIds, edgesChangedIds, copiedText, iconsChanged, preRuns, postRuns, false, isUndoRedo);
	}

	/**
	 * Redraws the map, then displays it
	 */
	private void innerCreateAndShowMap(UpdateType updateType, Set<Integer> centersChangedIds, Set<Integer> edgesChangedIds, List<MapText> textChanged, List<FreeIcon> iconsChanged,
			List<Runnable> preRuns, List<Runnable> postRuns, boolean isLowPriorityChange, boolean isUndoRedo)
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

		// Incremental updates require an existing rendered map to patch — if there's no base map,
		// the update would crash. The original concern was the "opening a new map" path, where
		// cancel() sets isMapReadyForInteractions=false AND a later loadSettings nulls mapFromMapCreator.
		// Using !isMapReadyForInteractions as the proxy is wrong though: it also fires false-positive
		// in the success-path tail of a non-interactive draw — done() clears isMapBeingDrawn before
		// recursively starting the next queued Incremental, but isMapReadyForInteractions is still
		// false (set by the start of the just-finished non-interactive draw and only restored when
		// the chain drains). Polling the next update out of the queue and then dropping it here
		// strands the flag false forever and locks the editor out of all interactions. Check the
		// actual base-map state instead, which is what the comment was describing all along.
		if (updateType == UpdateType.Incremental && !isMapBeingDrawn && getCurrentMapForIncrementalUpdate() == null)
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
					incrementalUpdatesToDraw.add(new MapUpdate(updateType, centersChangedIds, edgesChangedIds, textChanged, iconsChanged, preRuns, postRuns, isLowPriorityChange, isUndoRedo));
				}
				else
				{
					nonIncrementalUpdatesToDraw.add(new MapUpdate(updateType, centersChangedIds, edgesChangedIds, textChanged, iconsChanged, preRuns, postRuns, isLowPriorityChange, isUndoRedo));
				}

				lowPriorityUpdatesToDraw.add(currentUpdate);
				return;
			}
			else
			{
				if (isLowPriorityChange)
				{
					lowPriorityUpdatesToDraw.add(new MapUpdate(updateType, centersChangedIds, edgesChangedIds, textChanged, iconsChanged, preRuns, postRuns, isLowPriorityChange, isUndoRedo));
				}
				else
				{
					if (updateType == UpdateType.Incremental)
					{
						incrementalUpdatesToDraw.add(new MapUpdate(updateType, centersChangedIds, edgesChangedIds, textChanged, iconsChanged, preRuns, postRuns, isLowPriorityChange, isUndoRedo));
					}
					else
					{
						nonIncrementalUpdatesToDraw.add(new MapUpdate(updateType, centersChangedIds, edgesChangedIds, textChanged, iconsChanged, preRuns, postRuns, isLowPriorityChange, isUndoRedo));
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
						currentUpdate = new MapUpdate(updateType, centersChangedIds, edgesChangedIds, textChanged, iconsChanged, preRuns, postRuns, isLowPriorityChange, isUndoRedo);

						clearMapPartsAsNeeded(updateType);

						if (updateType == UpdateType.Incremental)
						{
							Image map = getCurrentMapForIncrementalUpdate();
							IntRectangle combinedReplaceBounds = null;
							// The expensive part of an incremental update builds a self-contained snippet that never touches the shared
							// map buffer; only the brief final blit of that snippet into the buffer does. So rather than locking the whole
							// update against a concurrent display reader (which would serialize the slow snippet computation with an
							// in-flight rescale), hand each MapCreator the write lock and let it lock only around that blit. The
							// computation then runs in parallel with the rescale, and only the pixel handoff serializes.
							if (centersChangedIds != null && centersChangedIds.size() > 0 || edgesChangedIds != null && edgesChangedIds.size() > 0)
							{
								Stopwatch incrementalUpdateTimer = new Stopwatch("do incremental update for centers and edges");
								currentMapCreator = new MapCreator();
								currentMapCreator.setIncrementalMapWriteLock(mapBufferLock.writeLock());
								IntRectangle replaceBounds = currentMapCreator.incrementalUpdateForCentersAndEdges(settings, mapParts, map, centersChangedIds, edgesChangedIds, isLowPriorityChange);
								combinedReplaceBounds = combinedReplaceBounds == null ? replaceBounds : combinedReplaceBounds.add(replaceBounds);
								if (DebugFlags.printIncrementalUpdateTimes())
								{
									incrementalUpdateTimer.printElapsedTime();
								}
							}

							if (textChanged != null && textChanged.size() > 0)
							{
								Stopwatch incrementalUpdateTimer = new Stopwatch("do incremental update for text");
								currentMapCreator = new MapCreator();
								currentMapCreator.setIncrementalMapWriteLock(mapBufferLock.writeLock());
								IntRectangle replaceBounds = currentMapCreator.incrementalUpdateText(settings, mapParts, map, textChanged);
								combinedReplaceBounds = combinedReplaceBounds == null ? replaceBounds : combinedReplaceBounds.add(replaceBounds);
								if (DebugFlags.printIncrementalUpdateTimes())
								{
									incrementalUpdateTimer.printElapsedTime();
								}
							}

							if (iconsChanged != null && iconsChanged.size() > 0)
							{
								Stopwatch incrementalUpdateTimer = new Stopwatch("do incremental update for icons");
								currentMapCreator = new MapCreator();
								currentMapCreator.setIncrementalMapWriteLock(mapBufferLock.writeLock());
								IntRectangle replaceBounds = currentMapCreator.incrementalUpdateIcons(settings, mapParts, map, iconsChanged);
								combinedReplaceBounds = combinedReplaceBounds == null ? replaceBounds : combinedReplaceBounds.add(replaceBounds);
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
				try
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
							int scaledBorderWidth = settings.drawBorder && settings.borderPosition == BorderPosition.Outside_map ? (int) (settings.borderWidth * settings.resolution) : 0;
							if (replaceBounds != null)
							{
								onFinishedDrawingIncremental(anotherDrawIsQueued, scaledBorderWidth, replaceBounds, warningMessages);
							}
							else
							{
								onFinishedDrawingFull(map, anotherDrawIsQueued, scaledBorderWidth, warningMessages, result.citiesRemovedForTouchingWater, isUndoRedo);
							}
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
							innerCreateAndShowMap(next.updateType, next.centersChangedIds, next.edgesChangedIds, next.textChanged, next.iconsChanged, next.preRuns, next.postRuns, next.isLowPriority, next.isUndoRedo);
						}
						else
						{
							isMapReadyForInteractions = true;

							while (tasksToRunWhenMapReady.size() > 0 && isMapReadyForInteractions)
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
							Exception exception = result != null ? result.exception : null;
							onFailedToDraw(exception);
						}
						currentMapCreator = null;
						currentUpdate = null;
						isMapBeingDrawn = false;

						if (isCanceled)
						{
							MapUpdate next = combineAndGetNextUpdateToDraw();
							if (next != null)
							{
								innerCreateAndShowMap(next.updateType, next.centersChangedIds, next.edgesChangedIds, next.textChanged, next.iconsChanged, next.preRuns, next.postRuns,
										next.isLowPriority, next.isUndoRedo);
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
				catch (RuntimeException ex)
				{
					SwingHelper.handleException(ex, null, false);
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
		catch (RuntimeException e)
		{
			Logger.printError("Map creation failed.", e);
			return new UpdateResult(null, null, new ArrayList<>(), e);
		}

		System.gc();
		UpdateResult result = new UpdateResult(map, null, currentMapCreator.getWarningMessages());
		result.citiesRemovedForTouchingWater = currentMapCreator.getCitiesRemovedForTouchingWater();
		return result;
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
		/** City icons dropped because they landed on water (full draws only); empty otherwise. */
		public List<IconDrawer.CityIconRemovedForWater> citiesRemovedForTouchingWater = new ArrayList<>();
		public Exception exception;

		public UpdateResult(Image map, IntRectangle replaceBounds, List<String> warningMessages)
		{
			this.map = map;
			this.replaceBounds = replaceBounds;
			this.warningMessages = warningMessages;
		}

		public UpdateResult(Image map, IntRectangle replaceBounds, List<String> warningMessages, Exception exception)
		{
			this.map = map;
			this.replaceBounds = replaceBounds;
			this.warningMessages = warningMessages;
			this.exception = exception;
		}
	}

	protected abstract void onBeginDraw();

	protected void onDrawSubmitted(UpdateType updateType)
	{
	}

	public abstract MapSettings getSettingsFromGUI();

	/**
	 * Called when a full draw finishes (on the EDT). {@code citiesRemovedForWater} is the city icons that draw dropped because they landed on
	 * water (duplicates kept, so the size is the number of cities lost; empty when none). {@code wasTriggeredByUndoRedo} is true when the draw
	 * was caused by an undo or redo rather than a forward change. Both are per-draw values carried with this specific draw, so they are
	 * correct even when draws are queued or coalesced.
	 */
	protected abstract void onFinishedDrawingFull(Image map, boolean anotherDrawIsQueued, int borderPaddingAsDrawn, List<String> warningMessages,
			List<IconDrawer.CityIconRemovedForWater> citiesRemovedForWater, boolean wasTriggeredByUndoRedo);

	protected abstract void onFinishedDrawingIncremental(boolean anotherDrawIsQueued, int borderPaddingAsDrawn, IntRectangle incrementalChangeArea, List<String> warningMessages);

	protected abstract void onFailedToDraw(Exception exception);

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

	/**
	 * Lock to hold while reading pixels from the buffer returned by {@link #getCurrentMapForIncrementalUpdate()} from a thread other than
	 * the one running incremental updates (e.g. a background display rescale), so that read doesn't race with an in-place incremental
	 * mutation of the same buffer. Full draws don't mutate that buffer, so they don't contend for this lock.
	 */
	public Lock getMapReadLock()
	{
		return mapBufferLock.readLock();
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
		/** True if this draw was triggered by an undo or redo. Travels with the draw so its completion can be identified in done(). */
		boolean isUndoRedo;

		public MapUpdate(UpdateType updateType, Set<Integer> centersChangedIds, Set<Integer> edgesChangedIds, List<MapText> textChanged, List<FreeIcon> iconsChanged, List<Runnable> preRuns,
				List<Runnable> postRuns, boolean isLowPriority, boolean isUndoRedo)
		{
			this.isUndoRedo = isUndoRedo;
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

			// If any combined update came from an undo/redo, the combined draw is treated as undo/redo too.
			isUndoRedo = isUndoRedo || other.isUndoRedo;

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
			catch (RuntimeException ex)
			{
				SwingHelper.handleException(ex, null, false);
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

	public void doWhenMapIsNotDrawing(Runnable action)
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

	public boolean isMapReadyForInteractions()
	{
		return isMapReadyForInteractions;
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
