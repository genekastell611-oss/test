package com.tripfuel.app;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TrailboundTripMathTest {
    @Test public void adjustedMpgUsesRequestedPayloadPenaltyAndCap() {
        assertEquals(30.0, TrailboundTripMath.adjustedMpg(30, 0), 0.0001);
        assertEquals(29.4, TrailboundTripMath.adjustedMpg(30, 200), 0.0001);
        assertEquals(22.5, TrailboundTripMath.adjustedMpg(30, 5000), 0.0001);
    }

    @Test public void roundTripUsesActualReturnAndLegacyFallback() {
        assertEquals(210.0, TrailboundTripMath.roundMiles(100, 110), 0.0001);
        assertEquals(200.0, TrailboundTripMath.roundMiles(100, 0), 0.0001);
        assertEquals(0.0, TrailboundTripMath.roundMiles(0, 100), 0.0001);
    }

    @Test public void gallonsUseFullRoundTripDistance() {
        double gallons = TrailboundTripMath.gallonsConsumed(210, 29.4);
        assertEquals(7.142857, gallons, 0.0001);
    }

    @Test public void reserveIsFifteenPercentWithBounds() {
        assertEquals(2.175, TrailboundTripMath.reserveGallons(14.5), 0.0001);
        assertEquals(0.3, TrailboundTripMath.reserveGallons(1.0), 0.0001);
    }

    @Test public void startingFuelChangesStopTimingNotConsumption() {
        double mpg = 29.4;
        double round = 1000;
        assertEquals(round / mpg, TrailboundTripMath.gallonsConsumed(round, mpg), 0.0001);
        List<Double> stops = TrailboundTripMath.fuelStopMiles(round, mpg, 14.5, 12.0);
        assertEquals(2, stops.size());
        assertEquals(288.855, stops.get(0), 0.01);
        assertEquals(651.21, stops.get(1), 0.02);
    }

    @Test public void lowDepartureFuelCreatesImmediateFuelStop() {
        List<Double> stops = TrailboundTripMath.fuelStopMiles(500, 25, 15, 1);
        assertTrue(stops.size() >= 1);
        assertEquals(0.0, stops.get(0), 0.0001);
    }

    @Test public void oilIntervalPercentUsesConfiguredInterval() {
        assertEquals(50.0, TrailboundTripMath.oilIntervalPercent(50000, 52500, 5000), 0.0001);
        assertEquals(25.0, TrailboundTripMath.oilIntervalPercent(50000, 52500, 10000), 0.0001);
        assertEquals(0.0, TrailboundTripMath.oilIntervalPercent(53000, 52500, 5000), 0.0001);
    }

    @Test public void totalsNeverGoNegativeFromBadInputs() {
        assertEquals(125.0, TrailboundTripMath.tripTotal(50, 25, -10, 50, true), 0.0001);
        assertEquals(75.0, TrailboundTripMath.tripTotal(50, 25, -10, 50, false), 0.0001);
    }
}
