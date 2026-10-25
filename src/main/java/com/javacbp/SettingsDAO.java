package com.javacbp;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;

public class SettingsDAO {
    private final MongoCollection<Document> collection;
    public SettingsDAO(MongoDatabase database) { collection = database.getCollection("settings"); }
    public void saveSettings(Settings settings) {
        Document doc = new Document("state", settings.getState())
                .append("monthlyBudget", settings.getMonthlyBudget())
                .append("month", settings.getMonth());
        collection.updateOne(new Document("month", settings.getMonth()), new Document("$set", doc), new UpdateOptions().upsert(true));
    }
}
