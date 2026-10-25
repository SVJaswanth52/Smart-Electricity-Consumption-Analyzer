package com.javacbp;
public class Usage {

    private String applianceName;
    private String month;
    private double dailyUsageHours;

    public Usage(
            String applianceName,
            String month,
            double dailyUsageHours) {

        this.applianceName = applianceName;
        this.month = month;
        this.dailyUsageHours = dailyUsageHours;
    }

    public String getApplianceName() {
        return applianceName;
    }

    public String getMonth() {
        return month;
    }

    public double getDailyUsageHours() {
        return dailyUsageHours;
    }
}