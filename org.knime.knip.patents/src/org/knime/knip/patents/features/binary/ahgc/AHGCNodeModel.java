package org.knime.knip.patents.features.binary.ahgc;

import java.util.ArrayList;
import java.util.List;

import net.imagej.ImgPlus;
import net.imglib2.Cursor;
import net.imglib2.type.logic.BitType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.knime.core.data.DataType;
import org.knime.core.data.collection.CollectionCellFactory;
import org.knime.core.data.collection.ListCell;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.node.defaultnodesettings.SettingsModel;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.knip.base.data.img.ImgPlusValue;
import org.knime.knip.base.node.ValueToCellNodeModel;

/**
 * Adaptive Hierarchical Geometric Centroids
 *
 * @author Daniel Seebacher, University of Konstanz
 *
 * @see Mai Yang, Guoping Qiu, Jiwu Huang, Dave Elliman,
 *      "Near-Duplicate Image Recognition and Content-based Image Retrieval using Adaptive Hierarchical Geometric Centroids,"
 *      Pattern Recognition, International Conference on, pp. 958-961, 18th
 *      International Conference on Pattern Recognition (ICPR'06) Volume 2, 2006
 */
public class AHGCNodeModel extends
        ValueToCellNodeModel<ImgPlusValue<BitType>, ListCell> {

    /**
     *
     * @return The SettingsModel for the Levels
     */
    static SettingsModelIntegerBounded createLevelModel() {
        return new SettingsModelIntegerBounded("m_level", 3, 1,
                Integer.MAX_VALUE);
    }

    /**
     *
     * @return The SettingsModel to use white as the background color.
     */
    static SettingsModelBoolean createWhiteAsBackgroundModel() {
        return new SettingsModelBoolean("m_foreground", true);
    }

    private final SettingsModelIntegerBounded m_levels = createLevelModel();
    private final SettingsModelBoolean m_whiteAsForeground = createWhiteAsBackgroundModel();

    @Override
    protected void addSettingsModels(final List<SettingsModel> settingsModels) {
        settingsModels.add(this.m_levels);
        settingsModels.add(this.m_whiteAsForeground);
    }

    @Override
    protected ListCell compute(final ImgPlusValue<BitType> cellValue)
            throws Exception {

        final ImgPlus<BitType> img = cellValue.getImgPlus();

        // the first region is the whole image
        List<Region> regions = new ArrayList<>();
        regions.add(new Region(Views.interval(img,
                new long[] { img.min(0), img.min(1) }, new long[] { img.max(0),
                        img.max(1) })));

        // for each level
        final List<double[]> fv = new ArrayList<>();
        for (int i = 0; i < this.m_levels.getIntValue(); i++) {

            // calculate the centroid for each region
            final List<Centroid> centroids = new ArrayList<>();
            for (final Region region : regions) {
                centroids.add(getCentroid(region));
            }

            // centroid x,y are feature values. normalize.
            for (final Centroid centroid : centroids) {
                final double relative_x = (double) centroid.x
                        / img.dimension(0);
                final double relative_y = (double) centroid.y
                        / img.dimension(1);
                fv.add(new double[] { relative_x, relative_y });
            }

            // divide each region into four subregions
            final List<Region> subregions = new ArrayList<>();
            for (int l = 0; l < regions.size(); l++) {
                final Centroid centroid = centroids.get(l);
                subregions.addAll(getSubRegions(regions.get(l), centroid));
            }

            // subregions are new regions.
            regions = subregions;
        }

        final List<DoubleCell> cells = new ArrayList<>();
        for (int i = 0; i < fv.size(); i++) {
            final double[] centroid = fv.get(i);
            cells.add(new DoubleCell(centroid[0]));
            cells.add(new DoubleCell(centroid[1]));
        }

        return CollectionCellFactory.createListCell(cells);
    }

    /**
     * Divides one region into four subregions using the centroid.
     *
     * @param region
     *            a region
     * @param cx
     *            the x value of the centroid
     * @param cy
     *            the y value of the centroid
     * @return the four subregions
     */
    private List<Region> getSubRegions(final Region region,
            final Centroid centroid) {

        final List<Region> subregions = new ArrayList<>();

        // top left
        subregions.add(new Region(Views.interval(region.interval, new long[] {
                region.interval.min(0), region.interval.min(1) }, new long[] {
                centroid.x, centroid.y })));

        // top right
        subregions.add(new Region(Views.interval(region.interval, new long[] {
                centroid.x, region.interval.min(1) }, new long[] {
                region.interval.max(0), centroid.y })));

        // bottom left
        subregions.add(new Region(Views.interval(region.interval, new long[] {
                region.interval.min(0), centroid.y }, new long[] { centroid.x,
                region.interval.max(1) })));

        // bottom right
        subregions.add(new Region(Views.interval(region.interval, new long[] {
                centroid.x, centroid.y }, new long[] { region.interval.max(0),
                region.interval.max(1) })));

        for (final Region subregion : subregions) {
            subregion.isEmpty();
        }

        return subregions;
    }

    /**
     * Calculates the centroid for a region. If the region is empty use center
     * of the region.
     *
     * @param region
     *            a region
     * @return the centroid of the region
     */
    private Centroid getCentroid(final Region region) {

        // if we know that the region is empty, return center
        if (region.isEmpty()) {
            return getEmptyRegionCentroid(region);
        }

        double cx = 0;
        double cy = 0;
        double sum = 0;

        final Cursor<BitType> cursor = region.interval.localizingCursor();
        while (cursor.hasNext()) {
            final BitType next = cursor.next();
            if (next.get() ^ this.m_whiteAsForeground.getBooleanValue()) {
                cx += cursor.getLongPosition(0);
                cy += cursor.getLongPosition(1);
                sum++;
            }
        }

        // if a region is empty use the default centroid
        if (sum == 0) {
            region.setEmpty(true);
            return getEmptyRegionCentroid(region);
        }

        return new Centroid((long) (cx / sum), (long) (cy / sum));
    }

    public Centroid getEmptyRegionCentroid(final Region region) {
        if (!region.isEmpty()) {
            throw new IllegalArgumentException("Region isn't empty");
        }

        return new Centroid(
                region.interval.min(0)
                        + ((region.interval.max(0) - region.interval.min(0)) / 2),
                region.interval.min(1)
                        + ((region.interval.max(1) - region.interval.min(1)) / 2));
    }

    @Override
    protected DataType getOutDataCellListCellType() {
        return DoubleCell.TYPE;
    }

    private final class Region {

        private final IntervalView<BitType> interval;
        private boolean empty = false;

        public Region(final IntervalView<BitType> interval) {
            this.interval = interval;
        }

        public boolean isEmpty() {
            return this.empty;
        }

        public void setEmpty(final boolean empty) {
            this.empty = empty;
        }
    }

    private final class Centroid {

        private final long x;
        private final long y;

        public Centroid(final long x, final long y) {
            this.x = x;
            this.y = y;
        }

    }
}
