
package com.javacbp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class SuggestionService {

    public List<Map<String, Object>> makeSuggestions(
            List<Map<String, Object>> appliances,
            double budget,
            double estimatedBill) {

        List<Map<String, Object>> suggestions = new ArrayList<>();

        if (appliances == null || appliances.isEmpty()) {
            suggestions.add(Map.of(
                    "title", "Add appliance usage",
                    "detail", "Enter at least one appliance and its daily usage to receive consumption and saving suggestions."
            ));
            return suggestions;
        }

        List<Map<String, Object>> ranked = new ArrayList<>(appliances);

        ranked.sort(Comparator.comparingDouble(
                a -> -((Number) a.get("monthlyConsumption")).doubleValue()));

        double totalUnits = 0;

        for (Map<String, Object> appliance : appliances) {
            totalUnits += ((Number) appliance.get("monthlyConsumption")).doubleValue();
        }

        Map<String, Object> top = ranked.get(0);

        suggestions.add(Map.of(
                "title", "Highest energy consumer: " + top.get("name"),
                "detail", String.format(
                        "This appliance uses %.2f kWh per month, out of %.2f kWh for all appliances.",
                        ((Number) top.get("monthlyConsumption")).doubleValue(),
                        totalUnits)
        ));

        Map<String, Object> controllable = null;

        for (Map<String, Object> appliance : ranked) {
            String name = appliance.get("name").toString().toLowerCase();

            if (!name.contains("refrigerator") && !name.contains("fridge")) {
                controllable = appliance;
                break;
            }
        }

        if (controllable != null) {
            String name = controllable.get("name").toString();
            String lowerName = name.toLowerCase();

            double units = ((Number) controllable.get("monthlyConsumption")).doubleValue();
            double hours = ((Number) controllable.get("dailyUsageHours")).doubleValue();
            double watts = ((Number) controllable.get("powerWatts")).doubleValue();
            int days = ((Number) controllable.get("daysInMonth")).intValue();

            if (watts > 0 && hours > 0 && days > 0) {
                double reductionHours = Math.min(0.5, hours);
                double savedUnits = watts * reductionHours * days / 1000.0;

                String advice;

                if (lowerName.contains("ac") || lowerName.contains("air conditioner")) {
                    advice = "Try reducing AC usage by 30 minutes per day, if comfortable.";
                } else if (lowerName.contains("fan")) {
                    advice = "Reduce unnecessary fan usage by 30 minutes per day.";
                } else if (lowerName.contains("tube") || lowerName.contains("bulb")
                        || lowerName.contains("light")) {
                    advice = "Switch off this light when the room is not in use.";
                } else if (lowerName.contains("charger")) {
                    advice = "Unplug the charger when it is not needed.";
                } else if (lowerName.contains("washing machine")) {
                    advice = "Run full loads when practical instead of unnecessary small loads.";
                } else {
                    advice = "Reduce unnecessary operating time where practical.";
                }

                suggestions.add(Map.of(
                        "title", "Save energy with " + name,
                        "detail", String.format(
                                "%s Estimated potential saving: %.2f kWh per month. Current usage is %.2f kWh per month.",
                                advice, savedUnits, units)
                ));
            }
        }

        if (estimatedBill > budget) {
            double excess = estimatedBill - budget;
            double averageRate = totalUnits > 0 ? estimatedBill / totalUnits : 0;
            double requiredSavings = averageRate > 0 ? excess / averageRate : 0;

            suggestions.add(Map.of(
                    "title", "Budget exceeded",
                    "detail", String.format(
                            "Your estimated monthly energy-charge amount exceeds your monthly budget by ₹%.2f. Saving about %.2f kWh is a rough guide only; because tariffs use slabs, reducing usage may not lower the bill proportionally. Re-analyze after changing usage to compare the estimate.",
                            excess, requiredSavings)
            ));
        } else {
            suggestions.add(Map.of(
                    "title", "Within budget",
                    "detail", String.format(
                            "The estimated monthly energy-charge amount is ₹%.2f below your ₹%.2f monthly budget. This excludes unverified bill components, so it is not a guarantee that the actual bill will be within budget.",
                            budget - estimatedBill, budget)
            ));
        }

        for (Map<String, Object> appliance : ranked) {
            String name = appliance.get("name").toString().toLowerCase();

            if (name.contains("refrigerator") || name.contains("fridge")) {
                suggestions.add(Map.of(
                        "title", "Refrigerator efficiency",
                        "detail", "Keep the door sealed, allow ventilation around the fridge, and use an appropriate temperature setting. Do not reduce its operating hours."
                ));
                break;
            }
        }

        return suggestions;
    }
}
