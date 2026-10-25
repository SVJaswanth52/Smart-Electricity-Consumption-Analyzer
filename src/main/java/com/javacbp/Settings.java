package com.javacbp;

public class Settings {
    private String state;
    private double monthlyBudget;
    private String month;

    public Settings(String state, double monthlyBudget, String month) {
        this.state = state;
        this.monthlyBudget = monthlyBudget;
        this.month = month;
    }
    public String getState() { return state; }
    public double getMonthlyBudget() { return monthlyBudget; }
    public String getMonth() { return month; }
}
