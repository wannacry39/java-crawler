package com.example.crawler;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class TaskProducer {
    private final String queueName;
    private final ConnectionFactory factory;

    public TaskProducer(String host, String queueName) {
        this.queueName = queueName;
        this.factory = new ConnectionFactory();
        this.factory.setHost(host);
    }

    public void sendTask(String message) throws Exception {
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            channel.queueDeclare(queueName, true, false, false, null);
            channel.basicPublish("", queueName, null, message.getBytes());
            System.out.println("[TaskProducer] Sent: " + message);
        }
    }
}
