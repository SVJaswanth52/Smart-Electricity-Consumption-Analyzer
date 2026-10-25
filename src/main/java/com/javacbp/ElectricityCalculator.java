package com.javacbp;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class ElectricityCalculator {

    public int getDaysInMonth(String month) {
        if (month == null) {
            return 0;
        }

        switch (month.toLowerCase()) {
            case "january":
            case "march":
            case "may":
            case "july":
            case "august":
            case "october":
            case "december":
                return 31;

            case "april":
            case "june":
            case "september":
            case "november":
                return 30;

            case "february":
                return 28;

            default:
                return 0;
        }
    }

    public double calculateMonthlyConsumption(
            Appliance appliance,
            double dailyHours,
            int days
    ) {
        return appliance.getPowerWatts() * dailyHours * days / 1000.0;
    }

    public BillResult calculateBill(Document tariff, double units) {
        String method = tariff.getString("calculationMethod");

        double energyCost = 0;
        double billedUnits = units;

        if ("FLAT_RATE".equals(method)) {
            energyCost = units * number(tariff.get("flatRate"));

        } else if ("TELANGANA_CATEGORY_THEN_SLABS".equals(method)) {
            String category = units <= 100
                    ? "TOTAL_UP_TO_100"
                    : units <= 200
                    ? "TOTAL_101_TO_200"
                    : "TOTAL_ABOVE_200";

            energyCost = progressive(
                    tariff.getList("slabs", Document.class),
                    units,
                    category
            );

        } else if ("TELESCOPIC_UP_TO_250_THEN_WHOLE_BAND".equals(method)) {
            if (units <= 250) {
                energyCost = progressive(
                        tariff.getList("slabs", Document.class),
                        units,
                        "TOTAL_UP_TO_250"
                );
            } else {
                List<Document> bands =
                        tariff.getList("wholeConsumptionBands", Document.class);

                double rate = rateForTotal(
                        bands,
                        units,
                        "minTotalUnits",
                        "maxTotalUnits"
                );

                energyCost = units * rate;
            }

        } else if ("BIMONTHLY_TELESCOPIC_ESTIMATE".equals(method)) {
            billedUnits = units * 2;

            energyCost = progressive(
                    tariff.getList("slabs", Document.class),
                    billedUnits,
                    null
            );

        } else {
            energyCost = progressive(
                    tariff.getList("slabs", Document.class),
                    units,
                    null
            );
        }

        List<String> warnings = new ArrayList<>();
        boolean bimonthly = "BIMONTHLY_TELESCOPIC_ESTIMATE".equals(method);

        // Tamil Nadu's supplied schedule is bimonthly, while the user enters
        // average monthly usage and a monthly budget. Calculate the correct
        // two-month slab first, then convert the estimate to a monthly amount
        // so it can be compared fairly with the user's monthly budget.
        double monthlyFactor = bimonthly ? 0.5 : 1.0;
        energyCost *= monthlyFactor;

        Document charges = tariff.get("additionalCharges", Document.class);

        Double fixedCharge = knownNumber(charges, "fixedCharge");
        Double dutyRate = knownNumber(charges, "electricityDuty");
        Double otherAdjustments = knownNumber(charges, "otherAdjustments");
        Double subsidy = knownNumber(charges, "subsidy");

        // If verified charges are ever added for a bimonthly tariff, keep
        // those values on the same monthly-equivalent basis as energy cost.
        if (bimonthly) {
            if (fixedCharge != null) fixedCharge *= monthlyFactor;
            if (otherAdjustments != null) otherAdjustments *= monthlyFactor;
            if (subsidy != null) subsidy *= monthlyFactor;
        }

        Double dutyAmount = dutyRate == null
                ? null
                : round(energyCost * dutyRate / 100.0);

        boolean chargesComplete = fixedCharge != null
                && dutyAmount != null
                && otherAdjustments != null
                && subsidy != null;

        double finalBill = energyCost;

        if (fixedCharge != null) {
            finalBill += fixedCharge;
        }

        if (dutyAmount != null) {
            finalBill += dutyAmount;
        }

        if (otherAdjustments != null) {
            finalBill += otherAdjustments;
        }

        if (subsidy != null) {
            finalBill -= subsidy;
        }

        finalBill = Math.max(0, finalBill);

        warnings.add(
                "This is an estimate based on the available tariff data, not a guaranteed utility bill."
        );

        if (fixedCharge == null) {
            warnings.add(
                    "Fixed charges are unavailable or unverified and are excluded."
            );
        }

        if (dutyAmount == null) {
            warnings.add(
                    "Electricity duty is unavailable or unverified and is excluded."
            );
        }

        if (otherAdjustments == null) {
            warnings.add(
                    "Other adjustments are excluded because verified values are unavailable."
            );
        }

        if (subsidy == null) {
            warnings.add(
                    "Consumer-specific subsidies and credits are unavailable and are not included."
            );
        }

        if (!chargesComplete) {
            warnings.add(
                    "The displayed bill total includes only the known components and may differ from the actual bill."
            );
        }

        if (bimonthly) {
            warnings.add(
                    "The tariff slabs were applied to estimated two-month consumption. The displayed charge is half of that estimate so it can be compared with your monthly budget; your actual two-month bill may differ."
            );
        }

        return new BillResult(
                round(energyCost),
                fixedCharge == null ? null : round(fixedCharge),
                dutyAmount,
                otherAdjustments == null ? null : round(otherAdjustments),
                subsidy == null ? null : round(subsidy),
                round(finalBill),
                round(billedUnits),
                warnings,
                chargesComplete
        );
    }

    private double progressive(
            List<Document> slabs,
            double units,
            String category
    ) {
        double total = 0;

        if (slabs == null) {
            return total;
        }

        for (Document slab : slabs) {
            if (category != null
                    && !category.equals(slab.getString("categoryCondition"))) {
                continue;
            }

            double min = number(slab.get("minUnits"));

            Object maxValue = slab.get("maxUnits");
            double max = maxValue == null
                    ? Double.POSITIVE_INFINITY
                    : number(maxValue);

            double used = Math.max(0, Math.min(units, max) - min);

            total += used * number(slab.get("rate"));
        }

        return total;
    }

    private double rateForTotal(
            List<Document> bands,
            double units,
            String minKey,
            String maxKey
    ) {
        if (bands == null || bands.isEmpty()) {
            return 0;
        }

        for (Document band : bands) {
            double min = number(band.get(minKey));

            Object maxValue = band.get(maxKey);
            double max = maxValue == null
                    ? Double.POSITIVE_INFINITY
                    : number(maxValue);

            if (units > min && units <= max) {
                return number(band.get("rate"));
            }
        }

        return number(bands.get(bands.size() - 1).get("rate"));
    }

    private Double knownNumber(Document document, String key) {
        if (document == null || document.get(key) == null) {
            return null;
        }

        Object value = document.get(key);

        if (!(value instanceof Number)) {
            return null;
        }

        return ((Number) value).doubleValue();
    }

    private double number(Object value) {
        return value == null ? 0 : ((Number) value).doubleValue();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record BillResult(
            double energyCharge,
            Double fixedCharge,
            Double electricityDuty,
            Double otherAdjustments,
            Double subsidy,
            double finalBill,
            double billedUnits,
            List<String> warnings,
            boolean chargesComplete
    ) {}
}
