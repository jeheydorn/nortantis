package nortantis;

import nortantis.editor.CenterEdit;
import nortantis.editor.EdgeEdit;
import nortantis.editor.FreeIcon;
import nortantis.editor.RegionEdit;
import nortantis.editor.Road;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.swing.MapEdits;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Creates a new MapSettings for a zoomed-in sub-map of an existing map. The sub-map inherits the original map's land/water shape, region colors, text, icons, and roads within the selected area.
 */
public class SubMapCreator
{
	/**
	 * Creates a new MapSettings for a sub-map of the given original map.
	 *
	 * @param origSettings
	 *            The original map settings.
	 * @param origGraph
	 *            The original world graph (used for land/water lookup).
	 * @param origEdits
	 *            The original map edits (used for land/water, region, text, icons, roads).
	 * @param selBoundsRI
	 *            The selection bounds in resolution-invariant (RI) coordinates.
	 * @param subMapWorldSize
	 *            The number of Voronoi polygons for the sub-map.
	 * @return New MapSettings for the sub-map, with pre-populated edits.
	 */
	/**
	 * @param origResolution
	 * 		The resolution at which origGraph was created (i.e. the display quality scale), used to convert resolution-invariant coordinates to origGraph pixel coordinates.
	 */
	public static MapSettings createSubMapSettings(MapSettings origSettings, WorldGraph origGraph, MapEdits origEdits, Rectangle selBoundsRI, int subMapWorldSize, double origResolution, long seed)
	{
		// Step 1: Compute new dimensions and world size.
		// The largest dimension of the sub-map matches the largest dimension of the original map.
		// Whichever axis of the selection box is larger gets that max value; the other is scaled proportionally.
		int maxOrigDim = Math.max(origSettings.generatedWidth, origSettings.generatedHeight);
		int newGenWidth;
		int newGenHeight;
		if (selBoundsRI.width >= selBoundsRI.height)
		{
			newGenWidth = maxOrigDim;
			newGenHeight = (int) Math.round((double) maxOrigDim * selBoundsRI.height / selBoundsRI.width);
		}
		else
		{
			newGenHeight = maxOrigDim;
			newGenWidth = (int) Math.round((double) maxOrigDim * selBoundsRI.width / selBoundsRI.height);
		}
		newGenWidth = Math.max(1, newGenWidth);
		newGenHeight = Math.max(1, newGenHeight);

		int newWorldSize = Math.max(1, Math.min(SettingsGenerator.maxWorldSize, subMapWorldSize));

		// Step 2: Deep-copy original settings, override key fields.
		MapSettings newSettings = origSettings.deepCopyExceptEdits();
		newSettings.randomSeed = seed;
		newSettings.generatedWidth = newGenWidth;
		newSettings.generatedHeight = newGenHeight;
		newSettings.worldSize = newWorldSize;
		newSettings.imageExportPath = null;
		newSettings.heightmapExportPath = null;
		// No rotation/flip on the sub-map.
		newSettings.rightRotationCount = 0;
		newSettings.flipHorizontally = false;
		newSettings.flipVertically = false;
		// Initialize fresh empty edits so createGraphForUnitTests will create elevation (isInitialized=false).
		newSettings.edits = new MapEdits();

		// Step 3: Build the WorldGraph for the sub-map (to get center positions and count).
		// We call this with createElevationBiomesLakesAndRegions=false because land/water and icon placement will be determined by the source map, not by a new, generated world.
		// This gives us the same Voronoi structure MapCreator will use when rendering (same seed, same params).
		WorldGraph newGraph = MapCreator.createGraph(newSettings, false);

		// Step 5+6a: For each new center, sample its loc and all Voronoi corners in orig-graph space
		// and use majority/plurality voting to assign water/lake/region.
		MapEdits newEdits = new MapEdits();
		Map<Integer, List<Integer>> origRegionToNewCenters = new HashMap<>();

		for (Center newCenter : newGraph.centers)
		{
			// Build sample points: center loc + all Voronoi corners, mapped to orig-graph pixel space.
			List<Point> samplePts = new ArrayList<>(newCenter.corners.size() + 1);
			samplePts.add(mapToOrigGraphPoint(newCenter.loc, newGraph, selBoundsRI, origResolution));
			for (Corner corner : newCenter.corners)
			{
				samplePts.add(mapToOrigGraphPoint(corner.loc, newGraph, selBoundsRI, origResolution));
			}

			// Tally votes from the original map for each sample point.
			int waterVotes = 0;
			int lakeVotes = 0;
			Map<Integer, Integer> regionVotes = new HashMap<>();
			for (Point samplePt : samplePts)
			{
				Center origCenter = origGraph.findClosestCenter(samplePt, false);
				boolean sIsWater, sIsLake;
				Integer sRegionId;
				if (origCenter != null && origEdits.centerEdits.containsKey(origCenter.index))
				{
					CenterEdit oe = origEdits.centerEdits.get(origCenter.index);
					sIsWater = oe.isWater;
					sIsLake = oe.isLake;
					sRegionId = oe.regionId;
				}
				else if (origCenter != null)
				{
					sIsWater = origCenter.isWater;
					sIsLake = origCenter.isLake;
					sRegionId = origCenter.region != null ? origCenter.region.id : null;
				}
				else
				{
					sIsWater = true;
					sIsLake = false;
					sRegionId = null;
				}
				if (sIsWater) waterVotes++;
				if (sIsLake) lakeVotes++;
				if (sRegionId != null) regionVotes.merge(sRegionId, 1, Integer::sum);
			}

			// Majority vote: ≥50% water samples → water; ≥50% of water samples are lake → lake.
			boolean isWater = waterVotes * 2 >= samplePts.size();
			boolean isLake = isWater && waterVotes > 0 && lakeVotes * 2 >= waterVotes;
			// Plurality vote for region: the region with the most sample-point votes wins.
			Integer regionId = null;
			int maxVotes = 0;
			for (Map.Entry<Integer, Integer> e : regionVotes.entrySet())
			{
				if (e.getValue() > maxVotes)
				{
					maxVotes = e.getValue();
					regionId = e.getKey();
				}
			}

			// Apply to the new center (required before propagateCoastAndCornerFlags).
			newCenter.isWater = isWater;
			newCenter.isLake = isLake;

			if (regionId != null)
			{
				origRegionToNewCenters.computeIfAbsent(regionId, k -> new ArrayList<>()).add(newCenter.index);
			}
			newEdits.centerEdits.put(newCenter.index, new CenterEdit(newCenter.index, isWater, isLake, regionId, null, null));
		}

		// Propagate coast/corner flags now that isWater/isLake are set on all centers.
		newGraph.propagateCoastAndCornerFlags();
		newGraph.markLakes();

		// Step 6: Build remaining MapEdits.

		// RegionEdits — copy colors for all referenced original regionIds.
		for (Integer origRegionId : origRegionToNewCenters.keySet())
		{
			RegionEdit origRegionEdit = origEdits.regionEdits.get(origRegionId);
			if (origRegionEdit != null)
			{
				newEdits.regionEdits.put(origRegionId, new RegionEdit(origRegionId, origRegionEdit.color));
			}
			else if (origGraph.regions.containsKey(origRegionId))
			{
				nortantis.platform.Color color = origGraph.regions.get(origRegionId).backgroundColor;
				newEdits.regionEdits.put(origRegionId, new RegionEdit(origRegionId, color));
			}
		}

		// 6c: EdgeEdits — suppress all auto-generated rivers on the new graph (since we're inheriting land/water manually).
		// Any edge with river > 0 in the new graph gets a riverLevel=0 edit.
		for (Edge edge : newGraph.edges)
		{
			if (edge.river > 0)
			{
				newEdits.edgeEdits.put(edge.index, new EdgeEdit(edge.index, 0));
			}
		}

		// 6d: Transfer original river edges to the new graph.
		// Build set of river edges in the original (from edgeEdits overrides or edge.river).
		transferRivers(origGraph, origEdits, newGraph, selBoundsRI, newEdits, origResolution);

		// 6e: Text — copy MapText entries whose location falls inside selBoundsRI.
		newEdits.text = new CopyOnWriteArrayList<>();
		for (MapText text : origEdits.text)
		{
			if (selBoundsRI.containsOrOverlaps(text.location))
			{
				MapText newText = text.deepCopy();
				newText.location = transformRIPoint(text.location, selBoundsRI, newGenWidth, newGenHeight);
				// Clear bounds since they'll be recomputed at the new resolution.
				newText.line1Bounds = null;
				newText.line2Bounds = null;
				newEdits.text.add(newText);
			}
		}

		// 6f: FreeIcons — copy icons whose location falls inside selBoundsRI.
		for (FreeIcon icon : origEdits.freeIcons)
		{
			if (selBoundsRI.containsOrOverlaps(icon.locationResolutionInvariant))
			{
				Point newLoc = transformRIPoint(icon.locationResolutionInvariant, selBoundsRI, newGenWidth, newGenHeight);

				// Remap centerIndex: find the new center nearest to the transformed location.
				Integer newCenterIndex = null;
				if (icon.centerIndex != null)
				{
					Point newGraphPt = new Point(newLoc.x * origResolution, newLoc.y * origResolution);
					Center nearestNewCenter = newGraph.findClosestCenter(newGraphPt, false);
					if (nearestNewCenter != null)
					{
						newCenterIndex = nearestNewCenter.index;
					}
				}

				FreeIcon newIcon = new FreeIcon(newLoc, icon.scale, icon.type, icon.artPack, icon.groupId, icon.iconIndex, icon.iconName, newCenterIndex, icon.density, icon.fillColor,
						icon.filterColor, icon.maximizeOpacity, icon.fillWithColor, icon.originalScale);
				newEdits.freeIcons.addOrReplace(newIcon);
			}
		}
		newEdits.hasIconEdits = true;

		// 6g: Roads — keep roads that have at least 2 points inside selBoundsRI; transform coordinates.
		for (Road road : origEdits.roads)
		{
			List<Point> newPath = new ArrayList<>();
			for (Point pt : road.path)
			{
				if (selBoundsRI.containsOrOverlaps(pt))
				{
					newPath.add(transformRIPoint(pt, selBoundsRI, newGenWidth, newGenHeight));
				}
			}
			if (newPath.size() >= 2)
			{
				newEdits.roads.add(new Road(newPath));
			}
		}

		// Step 7: Attach the new edits to the new settings.
		newSettings.edits = newEdits;

		return newSettings;
	}

	/**
	 * Maps a point from new-graph pixel space to orig-graph pixel space.
	 */
	private static Point mapToOrigGraphPoint(Point newGraphPt, WorldGraph newGraph, Rectangle selBoundsRI, double origResolution)
	{
		double origX = (newGraphPt.x / newGraph.bounds.width * selBoundsRI.width + selBoundsRI.x) * origResolution;
		double origY = (newGraphPt.y / newGraph.bounds.height * selBoundsRI.height + selBoundsRI.y) * origResolution;
		return new Point(origX, origY);
	}

	/**
	 * Transfers rivers from the original graph into the new graph's edge edits.
	 */
	private static void transferRivers(WorldGraph origGraph, MapEdits origEdits, WorldGraph newGraph, Rectangle selBoundsRI, MapEdits newEdits, double origResolution)
	{
		// Determine effective river level per edge in the original graph.
		Map<Integer, Integer> origRiverLevels = new HashMap<>();
		for (Edge edge : origGraph.edges)
		{
			if (edge.river > 0)
			{
				origRiverLevels.put(edge.index, edge.river);
			}
		}
		for (EdgeEdit ee : origEdits.edgeEdits.values())
		{
			if (ee.riverLevel > 0)
			{
				origRiverLevels.put(ee.index, ee.riverLevel);
			}
			else if (ee.riverLevel == 0)
			{
				origRiverLevels.remove(ee.index);
			}
		}

		if (origRiverLevels.isEmpty())
		{
			return;
		}

		// For each original river edge, map its Voronoi corners (v0, v1) to the closest new-graph corners,
		// then trace the Voronoi-edge path between them. One original edge may span several new edges
		// (because the new graph is more detailed), so findPathGreedy gives the full chain. Adjacent
		// original edges share a corner; they map to the same new corner, so their paths connect.
		for (Map.Entry<Integer, Integer> entry : origRiverLevels.entrySet())
		{
			int origEdgeIndex = entry.getKey();
			int riverLevel = entry.getValue();

			Edge origEdge = origGraph.edges.get(origEdgeIndex);
			if (origEdge == null || origEdge.v0 == null || origEdge.v1 == null)
			{
				continue;
			}

			// Convert corner positions to RI space and check selection bounds.
			double origRI_v0x = origEdge.v0.loc.x / origResolution;
			double origRI_v0y = origEdge.v0.loc.y / origResolution;
			double origRI_v1x = origEdge.v1.loc.x / origResolution;
			double origRI_v1y = origEdge.v1.loc.y / origResolution;
			if (!selBoundsRI.contains(origRI_v0x, origRI_v0y) && !selBoundsRI.contains(origRI_v1x, origRI_v1y))
			{
				continue;
			}

			// Map each original corner to new-graph pixel space and find the closest new-graph corner.
			double newV0x = (origRI_v0x - selBoundsRI.x) / selBoundsRI.width * newGraph.getWidth();
			double newV0y = (origRI_v0y - selBoundsRI.y) / selBoundsRI.height * newGraph.getHeight();
			double newV1x = (origRI_v1x - selBoundsRI.x) / selBoundsRI.width * newGraph.getWidth();
			double newV1y = (origRI_v1y - selBoundsRI.y) / selBoundsRI.height * newGraph.getHeight();

			Corner newCorner0 = newGraph.findClosestCorner(new Point(newV0x, newV0y));
			Corner newCorner1 = newGraph.findClosestCorner(new Point(newV1x, newV1y));
			if (newCorner0 == null || newCorner1 == null || newCorner0.equals(newCorner1))
			{
				continue;
			}

			// Trace the Voronoi-edge path between the two new corners and mark every edge on the path
			// as a river at the original river level.
			Set<Edge> pathEdges = newGraph.findPathGreedy(newCorner0, newCorner1);
			for (Edge pathEdge : pathEdges)
			{
				newEdits.edgeEdits.put(pathEdge.index, new EdgeEdit(pathEdge.index, riverLevel));
			}
		}
	}

	/**
	 * Transforms a point from original RI space to new RI space.
	 */
	private static Point transformRIPoint(Point origRI, Rectangle selBoundsRI, int newGenWidth, int newGenHeight)
	{
		double newX = (origRI.x - selBoundsRI.x) / selBoundsRI.width * newGenWidth;
		double newY = (origRI.y - selBoundsRI.y) / selBoundsRI.height * newGenHeight;
		return new Point(newX, newY);
	}
}
