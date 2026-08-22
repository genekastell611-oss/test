package com.tripfuel.app;

import java.util.ArrayList;
import java.util.List;

/** Pure, testable math used by Trailbound trip budgeting and fuel-stop planning. */
public final class TrailboundTripMath {
    private TrailboundTripMath() { }

    public static double nonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    /**
     * Planning estimate requested by the app: reduce base MPG by 1% per 100 lb
     * of trip payload, capped at a 25% reduction. This is an estimate, not an
     * EPA/manufacturer payload formula.
     */
    public static double adjustedMpg(double baseMpg, double payloadLb) {
        baseMpg = nonNegative(baseMpg);
        payloadLb = nonNegative(payloadLb);
        if (baseMpg <= 0) return 0;
        double penalty = Math.min(0.25, payloadLb / 100.0 * 0.01);
        return baseMpg * (1.0 - penalty);
    }

    /** Actual outbound + return miles; falls back to a mirrored return only for legacy/incomplete profiles. */
    public static double roundMiles(double outboundMiles, double returnMiles) {
        outboundMiles = nonNegative(outboundMiles);
        returnMiles = nonNegative(returnMiles);
        if (outboundMiles <= 0) return 0;
        if (returnMiles <= 0) returnMiles = outboundMiles;
        return outboundMiles + returnMiles;
    }

    public static double gallonsConsumed(double roundMiles, double adjustedMpg) {
        roundMiles = nonNegative(roundMiles);
        adjustedMpg = nonNegative(adjustedMpg);
        return adjustedMpg > 0 ? roundMiles / adjustedMpg : 0;
    }

    /** Keep about 15% of tank as reserve, at least 0.5 gal where practical, never over 30% of tank. */
    public static double reserveGallons(double tankGallons) {
        tankGallons = nonNegative(tankGallons);
        if (tankGallons <= 0) return 0;
        return Math.min(tankGallons * 0.30, Math.max(0.5, tankGallons * 0.15));
    }

    /**
     * Planned fill-up mileages for the complete round trip. Departure fuel
     * affects when the first fill-up occurs, not how many gallons the vehicle
     * physically consumes over the trip.
     */
    public static List<Double> fuelStopMiles(double roundMiles, double adjustedMpg,
                                             double tankGallons, double departureGallons) {
        ArrayList<Double> stops = new ArrayList<>();
        roundMiles = nonNegative(roundMiles);
        adjustedMpg = nonNegative(adjustedMpg);
        tankGallons = nonNegative(tankGallons);
        departureGallons = Math.min(tankGallons, nonNegative(departureGallons));
        if (roundMiles <= 0 || adjustedMpg <= 0 || tankGallons <= 0) return stops;

        double reserve = reserveGallons(tankGallons);
        double firstRange = Math.max(0, departureGallons - reserve) * adjustedMpg;
        double fullRange = Math.max(0.1, tankGallons - reserve) * adjustedMpg;
        if (fullRange <= 0) return stops;

        double next = firstRange;
        int guard = 0;
        while (next < roundMiles && guard++ < 100) {
            stops.add(Math.max(0, next));
            next += fullRange;
        }
        return stops;
    }

    /** Estimated percent of a user-selected service interval remaining until the entered due mileage. */
    public static double oilIntervalPercent(double currentMileage, double dueMileage, double intervalMiles) {
        currentMileage = nonNegative(currentMileage);
        dueMileage = nonNegative(dueMileage);
        intervalMiles = nonNegative(intervalMiles);
        if (dueMileage <= currentMileage || intervalMiles <= 0) return 0;
        return Math.max(0, Math.min(100, (dueMileage - currentMileage) / intervalMiles * 100.0));
    }

    public static double tripTotal(double gasCost, double snacks, double extras,
                                   double hotelCost, boolean includeHotel) {
        return nonNegative(gasCost) + nonNegative(snacks) + nonNegative(extras)
                + (includeHotel ? nonNegative(hotelCost) : 0);
    }
}
