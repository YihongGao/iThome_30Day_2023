package com.example.iThome_2024.config

import com.google.cloud.spring.pubsub.core.PubSubTemplate
import com.google.cloud.spring.pubsub.integration.AckMode
import com.google.cloud.spring.pubsub.integration.inbound.PubSubInboundChannelAdapter
import com.google.cloud.spring.pubsub.integration.outbound.PubSubMessageHandler
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage
import com.google.cloud.spring.pubsub.support.GcpPubSubHeaders
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.integration.channel.PublishSubscribeChannel
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessageHandler
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component


@Configuration
class PubSubConfig {
    companion object {
        private val logger = LoggerFactory.getLogger(PubSubConfig::class.java)
    }

    // Create a message channel for messages arriving from the subscription `sub-one`.
    @Bean
    fun inputMessageChannel(): MessageChannel {
        return PublishSubscribeChannel()
    }

    @Bean
    fun inboundChannelAdapter(
        @Qualifier("inputMessageChannel") messageChannel: MessageChannel,
        pubSubTemplate: PubSubTemplate,
        @Value("\${pubsub.ithome2024.day11.subscription}") subscriptionName : String,
    ): PubSubInboundChannelAdapter {
        val adapter =
            PubSubInboundChannelAdapter(pubSubTemplate, subscriptionName)
        adapter.outputChannel = messageChannel
        adapter.ackMode = AckMode.MANUAL
        adapter.payloadType = String::class.java
        return adapter
    }

    // Define what happens to the messages arriving in the message channel.
    @ServiceActivator(inputChannel = "inputMessageChannel")
    fun messageReceiver(
        payload: String,
        @Header(GcpPubSubHeaders.ORIGINAL_MESSAGE) message: BasicAcknowledgeablePubsubMessage,
    ) {
        logger.info("Message arrived via an inbound channel adapter from sub-one! Payload: $payload")
        Thread.sleep(1000 * 1)
        message.ack()
    }
}