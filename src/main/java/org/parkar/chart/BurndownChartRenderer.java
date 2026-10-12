package org.parkar.chart;

import org.parkar.service.BurndownSeries;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/** Renders a {@link BurndownSeries} to a PNG-ready {@link BufferedImage} using JFreeChart. */
public class BurndownChartRenderer {

    private static final Logger log = LoggerFactory.getLogger(BurndownChartRenderer.class);

    public static final int DEFAULT_WIDTH = 900;
    public static final int DEFAULT_HEIGHT = 480;

    public BufferedImage render(BurndownSeries series) {
        return render(series, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public BufferedImage render(BurndownSeries series, int width, int height) {
        log.debug("Rendering burndown chart for '{}' at {}x{} ({} data point(s))",
                series.owner(), width, height, series.dates().size());
        try {
            return doRender(series, width, height);
        } catch (RuntimeException e) {
            log.error("Failed to render burndown chart for '{}': {}", series.owner(), e.getMessage(), e);
            throw e;
        }
    }

    private BufferedImage doRender(BurndownSeries series, int width, int height) {
        TimeSeries ideal = new TimeSeries("Ideal");
        TimeSeries actual = new TimeSeries("Actual");
        for (int i = 0; i < series.dates().size(); i++) {
            Day day = new Day(toDate(series.dates().get(i)));
            ideal.add(day, series.ideal().get(i));
            actual.add(day, series.actual().get(i));
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(ideal);
        dataset.addSeries(actual);

        String title = (series.owner() == null || series.owner().isBlank() ? "All Developers" : series.owner())
                + " - Burndown (" + series.range().start() + " to " + series.range().end() + ")";

        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                title, "Date", "Remaining Tasks", dataset,
                true, true, false);
        chart.setBackgroundPaint(Color.WHITE);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(new Color(0xF5, 0xF5, 0xF5));
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        rangeAxis.setLowerBound(0);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true);
        renderer.setSeriesPaint(0, new Color(0x9E, 0x9E, 0x9E));
        renderer.setSeriesStroke(0, new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                BasicStroke.JOIN_MITER, new float[]{6f, 6f}, 0f));
        renderer.setSeriesPaint(1, new Color(0x1E, 0x88, 0xE5));
        renderer.setSeriesStroke(1, new BasicStroke(2.5f));
        plot.setRenderer(renderer);

        return chart.createBufferedImage(width, height);
    }

    private static Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
