package org.kie.pmml.commons.model;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;
import org.kie.pmml.api.enums.CLOSURE;
import org.kie.pmml.commons.model.expressions.KiePMMLInterval;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KiePMMLMiningFieldTest {

    @Test
    public void isAllowedValueNoLimit() {
        final KiePMMLMiningField kiePMMLMiningField = KiePMMLMiningField
                .builder("NAME", Collections.emptyList())
                .build();
        assertTrue(kiePMMLMiningField.isMatching(null));
        assertTrue(kiePMMLMiningField.isMatching("VALUE"));
    }

    @Test
    public void isAllowedValueWithAllowedValues() {
        final List<String> allowedValues = getCommonAllowedValues();
        final KiePMMLMiningField kiePMMLMiningField = KiePMMLMiningField
                .builder("NAME", Collections.emptyList())
                .withAllowedValues(allowedValues)
                .build();
        assertFalse(kiePMMLMiningField.isMatching(null));
        assertFalse(kiePMMLMiningField.isMatching("VALUE"));
        allowedValues.forEach(allowedValue -> assertTrue(kiePMMLMiningField.isMatching(allowedValue)));
    }

    @Test
    public void isAllowedValueWithIntervals() {
        final List<KiePMMLInterval> intervals = getCommonIntervals();
        final KiePMMLMiningField kiePMMLMiningField = KiePMMLMiningField
                .builder("NAME", Collections.emptyList())
                .withIntervals(intervals)
                .build();
        assertFalse(kiePMMLMiningField.isMatching(null));
        assertFalse(kiePMMLMiningField.isMatching("VALUE"));
        intervals.forEach(interval -> {
            double delta = (interval.getRightMargin().doubleValue() - interval.getLeftMargin().doubleValue()) / 2;
            Number toVerify = interval.getLeftMargin().doubleValue() + delta;
            assertTrue(kiePMMLMiningField.isMatching(toVerify));
        });
    }

    @Test
    public void isAllowedValueWithAllowedValuesAndIntervals() {
        final List<String> allowedValues = getCommonAllowedValues();
        final List<KiePMMLInterval> intervals = getCommonIntervals();
        final KiePMMLMiningField kiePMMLMiningField = KiePMMLMiningField
                .builder("NAME", Collections.emptyList())
                .withAllowedValues(allowedValues)
                .withIntervals(intervals)
                .build();
        assertFalse(kiePMMLMiningField.isMatching(null));
        assertFalse(kiePMMLMiningField.isMatching("VALUE"));
        allowedValues.forEach(allowedValue -> assertTrue(kiePMMLMiningField.isMatching(allowedValue)));
        intervals.forEach(interval -> {
            double delta = (interval.getRightMargin().doubleValue() - interval.getLeftMargin().doubleValue()) / 2;
            Number toVerify = interval.getLeftMargin().doubleValue() + delta;
            assertFalse(kiePMMLMiningField.isMatching(toVerify));
        });
    }

    private List<String> getCommonAllowedValues() {
        return IntStream
                .range(0, 3)
                .mapToObj(i -> "ALLOWED-" + i)
                .collect(Collectors.toList());
    }

    private List<KiePMMLInterval> getCommonIntervals() {
        final Random rnd = new Random();
        return IntStream
                .range(0, 3)
                .mapToObj(i -> {
                    final int leftMargin = rnd.nextInt(10);
                    final int rightMargin = leftMargin + rnd.nextInt(10);
                    final CLOSURE closure = CLOSURE.values()[rnd.nextInt(CLOSURE.values().length)];
                    return new KiePMMLInterval(leftMargin,
                                               rightMargin,
                                               closure);
                })
                .collect(Collectors.toList());
    }
}