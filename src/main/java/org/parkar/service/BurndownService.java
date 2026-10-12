package org.parkar.service;

import org.parkar.domain.DateRange;
import org.parkar.domain.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

/**
 * Task-count based burndown.
 *
 * <p>Remaining work on a given day equals the number of tasks that are not yet
 * {@code Done} as of that day (a task is considered done on its End Date). The
 * ideal line decreases linearly from the total task count to zero.</p>
 */
public class BurndownService {

    private static final Logger log = LoggerFactory.getLogger(BurndownService.class);

    public BurndownSeries compute(String developer, List<Task> tasks, DateRange range) {
        List<LocalDate> days = range.days();
        int total = tasks.size();
        log.debug("Computing burndown for '{}': {} task(s) over {} day(s) ({} to {})",
                developer, total, days.size(), range.start(), range.end());

        double[] ideal = new double[days.size()];
        double[] actual = new double[days.size()];

        for (int i = 0; i < days.size(); i++) {
            LocalDate day = days.get(i);
            final LocalDate d = day;
            actual[i] = tasks.stream().filter(t -> t.isRemainingOn(d)).count();

            if (days.size() == 1) {
                ideal[i] = 0;
            } else {
                ideal[i] = total * (1.0 - (double) i / (days.size() - 1));
            }
        }

        log.debug("Burndown computed for '{}': remaining from {} to {} across {} day(s)",
                developer, actual[0], actual[days.size() - 1], days.size());
        return new BurndownSeries(developer, range, days, box(ideal), box(actual), total);
    }

    private static List<Double> box(double[] values) {
        Double[] result = new Double[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i];
        }
        return List.of(result);
    }
}
