package com.javacbp;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoConfig {
    @Bean
    public MongoClient mongoClient(@Value("${app.mongodb.uri:mongodb://localhost:27017}") String uri) {
        return MongoClients.create(uri);
    }

    @Bean
    public MongoDatabase mongoDatabase(MongoClient client,
            @Value("${app.mongodb.database:java_cbp}") String databaseName) {
        return client.getDatabase(databaseName);
    }
}
