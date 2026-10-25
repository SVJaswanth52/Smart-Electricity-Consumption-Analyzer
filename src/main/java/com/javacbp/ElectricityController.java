package com.javacbp;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class ElectricityController {

    private final MongoDatabase database;
    private final ApplianceDAO applianceDAO;
    private final UsageDAO usageDAO;
    private final SettingsDAO settingsDAO;
    private final ElectricityCalculator calculator = new ElectricityCalculator();
    private final SuggestionService suggestionService = new SuggestionService();

    public ElectricityController(MongoDatabase database) {
        this.database = database;
        this.applianceDAO = new ApplianceDAO(database);
        this.usageDAO = new UsageDAO(database);
        this.settingsDAO = new SettingsDAO(database);
    }

    @GetMapping("/states")
    public List<String> getStates() {
        List<String> states = new ArrayList<>();

        for (Document document : database.getCollection("tariffs")
                .find()
                .sort(new Document("state", 1))) {
            states.add(document.getString("state"));
        }

        return states;
    }

    @GetMapping("/appliances")
    public List<Map<String, Object>> getAppliances() {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Appliance appliance : applianceDAO.getAllAppliances()) {
            result.add(Map.of(
                    "name", appliance.getName(),
                    "powerWatts", appliance.getPowerWatts()
            ));
        }

        return result;
    }

    @PostMapping("/appliances")
    public Map<String, Object> addAppliance(@RequestBody ApplianceRequest request) {
        if (request.name() == null || request.name().trim().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Appliance name is required."
            );
        }

        if (!Double.isFinite(request.powerWatts()) || request.powerWatts() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Power must be greater than zero."
            );
        }

        String name = request.name().trim();

        if (applianceDAO.applianceExists(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An appliance with this name already exists."
            );
        }

        applianceDAO.addAppliance(new Appliance(name, request.powerWatts()));

        return Map.of(
                "success", true,
                "message", "Appliance added successfully."
        );
    }

    @DeleteMapping("/appliances/{name}")
    public Map<String, Object> deleteAppliance(@PathVariable String name) {
        database.getCollection("appliances")
                .deleteOne(new Document("name", name));

        return Map.of(
                "success", true,
                "message", "Appliance removed if it existed."
        );
    }

    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestBody AnalyzeRequest request) {
        int days = calculator.getDaysInMonth(request.month());

        if (days == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choose a valid month."
            );
        }

        if (request.state() == null
                || request.monthlyBudget() <= 0
                || !Double.isFinite(request.monthlyBudget())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choose a state and enter a budget greater than zero."
            );
        }

        if (request.usages() == null || request.usages().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Enter daily usage for at least one appliance."
            );
        }

        Document tariff = database.getCollection("tariffs")
                .find(new Document("state", request.state())
                        .append("tariffPeriod", "FY2026-27"))
                .first();

        if (tariff == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tariff data was not found for this state."
            );
        }

        Settings settings = new Settings(
                request.state(),
                request.monthlyBudget(),
                request.month()
        );

        settingsDAO.saveSettings(settings);

        List<Map<String, Object>> results = new ArrayList<>();
        double totalUnits = 0;
        Set<String> usedApplianceNames = new HashSet<>();

        for (UsageInput input : request.usages()) {
            if (input.applianceName() == null
                    || !Double.isFinite(input.dailyUsageHours())
                    || input.dailyUsageHours() < 0
                    || input.dailyUsageHours() > 24) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Daily usage must be between 0 and 24 hours."
                );
            }

            String normalizedName = input.applianceName().trim().toLowerCase();
            if (!usedApplianceNames.add(normalizedName)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Each appliance can be entered only once per analysis: " + input.applianceName()
                );
            }

            Appliance selected = null;

            for (Appliance appliance : applianceDAO.getAllAppliances()) {
                if (appliance.getName().equalsIgnoreCase(input.applianceName())) {
                    selected = appliance;
                    break;
                }
            }

            if (selected == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown appliance: " + input.applianceName()
                );
            }

            Usage usage = new Usage(
                    selected.getName(),
                    request.month(),
                    input.dailyUsageHours()
            );

            usageDAO.saveUsage(usage);

            double units = calculator.calculateMonthlyConsumption(
                    selected,
                    input.dailyUsageHours(),
                    days
            );

            totalUnits += units;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", selected.getName());
            row.put("powerWatts", selected.getPowerWatts());
            row.put("dailyUsageHours", input.dailyUsageHours());
            row.put("monthlyConsumption", round(units));
            row.put("daysInMonth", days);

            results.add(row);
        }

        results.sort((a, b) -> Double.compare(
                ((Number) b.get("monthlyConsumption")).doubleValue(),
                ((Number) a.get("monthlyConsumption")).doubleValue()
        ));

        ElectricityCalculator.BillResult bill =
                calculator.calculateBill(tariff, totalUnits);

        double cost = bill.finalBill();

        for (Map<String, Object> row : results) {
            double share = totalUnits == 0
                    ? 0
                    : ((Number) row.get("monthlyConsumption")).doubleValue()
                    / totalUnits;

            row.put(
                    "estimatedEnergyChargeShare",
                    round(bill.energyCharge() * share)
            );
        }

        List<Map<String, Object>> suggestions =
                suggestionService.makeSuggestions(
                        results,
                        request.monthlyBudget(),
                        cost
                );

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("success", true);
        response.put("state", request.state());
        response.put("month", request.month());
        response.put("daysInMonth", days);

        response.put("totalConsumption", round(totalUnits));

        // Bill breakdown
        response.put("energyCharge", bill.energyCharge());
        response.put("fixedCharge", bill.fixedCharge());
        response.put("electricityDuty", bill.electricityDuty());
        response.put("otherAdjustments", bill.otherAdjustments());
        response.put("subsidy", bill.subsidy());
        response.put("finalBill", bill.finalBill());

        // Keep totalCost for compatibility with the existing frontend
        response.put("totalCost", cost);

        response.put("monthlyBudget", request.monthlyBudget());
        response.put("overBudget", cost > request.monthlyBudget());
        response.put(
                "budgetDifference",
                round(request.monthlyBudget() - cost)
        );

        response.put("billingCycle", tariff.getString("billingCycle"));
        response.put("billedUnits", bill.billedUnits());
        response.put("estimatedMonthlyEnergyCharges", bill.energyCharge());
        response.put("chargesComplete", bill.chargesComplete());
        response.put("calculationMethod", tariff.getString("calculationMethod"));

        response.put(
                "tariffVerificationStatus",
                tariff.getString("verificationStatus")
        );

        response.put("appliances", results);
        response.put("suggestions", suggestions);
        response.put("warnings", bill.warnings());
        response.put("tariffNotes", tariff.getString("notes"));

        // The complete consumer bill is not guaranteed to be covered.
        response.put("additionalChargesIncluded", false);

        return response;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleRequestError(ResponseStatusException ex) {
        return Map.of(
                "success", false,
                "message", ex.getReason() == null
                        ? "Request could not be processed."
                        : ex.getReason()
        );
    }

    private double round(double number) {
        return Math.round(number * 100.0) / 100.0;
    }

    public record ApplianceRequest(String name, double powerWatts) {}

    public record UsageInput(String applianceName, double dailyUsageHours) {}

    public record AnalyzeRequest(
            String state,
            double monthlyBudget,
            String month,
            List<UsageInput> usages){}
    
}