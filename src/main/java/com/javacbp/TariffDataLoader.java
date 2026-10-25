package com.javacbp;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class TariffDataLoader implements CommandLineRunner {
    private final MongoDatabase database;
    public TariffDataLoader(MongoDatabase database) { this.database = database; }

    @Override
    public void run(String... args) throws Exception {
        MongoCollection<Document> tariffs = database.getCollection("tariffs");
        ClassPathResource resource = new ClassPathResource("data/electricity-tariffs.json");
        try (InputStream input = resource.getInputStream()) {
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            List<Document> documents = Document.parse("{\"items\":" + json + "}").getList("items", Document.class);
            for (Document tariff : documents) {
                Document key = new Document("state", tariff.getString("state"))
                        .append("tariffPeriod", tariff.getString("tariffPeriod"));
                tariffs.replaceOne(key, tariff, new ReplaceOptions().upsert(true));
            }
        }
        System.out.println("Loaded/updated tariff data for " + tariffs.countDocuments() + " state records.");
    }
}
