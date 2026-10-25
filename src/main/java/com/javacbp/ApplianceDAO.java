package com.javacbp;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import java.util.ArrayList;

public class ApplianceDAO {

    private MongoCollection<Document> collection;

    public ApplianceDAO(MongoDatabase database) {
        collection = database.getCollection("appliances");
    }

    public boolean applianceExists(String name) {

        return collection.find(
                new Document("name", name)
        ).first() != null;
    }

    public void addAppliance(Appliance appliance) {

        if (applianceExists(appliance.getName())) {
            return;
        }

        Document document = new Document("name", appliance.getName())
                .append("powerWatts", appliance.getPowerWatts());

        collection.insertOne(document);
    }

    public ArrayList<Appliance> getAllAppliances() {

        ArrayList<Appliance> appliances = new ArrayList<>();

        for (Document document : collection.find()) {

            Object storedPower = document.get("powerWatts");
            double powerWatts = storedPower instanceof Number
                    ? ((Number) storedPower).doubleValue()
                    : 0;

            Appliance appliance = new Appliance(
                    document.getString("name"),
                    powerWatts
            );

            appliances.add(appliance);
        }

        return appliances;
    }
}