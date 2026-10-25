package com.javacbp;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;

public class UsageDAO {

    private MongoCollection<Document> collection;

    public UsageDAO(MongoDatabase database) {
        collection = database.getCollection("usage");
    }

    public void saveUsage(Usage usage) {

        Document document = new Document(
                "applianceName",
                usage.getApplianceName()
        )
        .append("month", usage.getMonth())
        .append("dailyUsageHours", usage.getDailyUsageHours());

        collection.updateOne(
                new Document("applianceName", usage.getApplianceName())
                        .append("month", usage.getMonth()),
                new Document("$set", document),
                new UpdateOptions().upsert(true)
        );
    }

    public double getUsage(
            String applianceName,
            String month) {

        Document document = collection.find(
                new Document("applianceName", applianceName)
                        .append("month", month)
        ).first();

        if (document == null) {
            return 0;
        }

        return document.getDouble("dailyUsageHours");
    }
}