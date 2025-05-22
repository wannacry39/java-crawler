package com.example.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import java.io.IOException;
import java.util.Map;

public class ResultConsumer {
    private final String queueName;
    private final ConnectionFactory factory;
    private final String elasticHost = "elasticsearch";
    private final int elasticPort = 9200;
    private final String elasticIndex = "news";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResultConsumer(String host, String queueName) {
        this.queueName = queueName;
        this.factory = new ConnectionFactory();
        this.factory.setHost(host);
    }

    public void consume() throws Exception {
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.queueDeclare(queueName, true, false, false, null);
        System.out.println("[ResultConsumer] Waiting for messages...");
        ElasticStorage storage = new ElasticStorage(elasticHost, elasticPort, elasticIndex);
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println("[ResultConsumer] Received: " + message);
            try {
                Map<String, Object> article = objectMapper.readValue(message, Map.class);
                String id = storage.computeId((String) article.get("title"), (String) article.get("pubDate"));
                storage.saveArticle(article, id);
            } catch (Exception e) {
                System.err.println("[ResultConsumer] Error saving to Elastic: " + e.getMessage());
            }
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        };
        channel.basicConsume(queueName, false, deliverCallback, consumerTag -> {});
    }
}
