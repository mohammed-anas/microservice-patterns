package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.client.InventoryClient;
import com.ecommerce.orderservice.client.PaymentClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Map;

/** Author: Mohammed Anas Date: 09/11/2026 */
@RestController
public class OrderController {

    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;

    @Value("${HOSTNAME:local}")
    private String containerId;

    public OrderController(PaymentClient paymentClient, InventoryClient inventoryClient) {
        this.paymentClient = paymentClient;
        this.inventoryClient = inventoryClient;
    }

    @GetMapping("/orders")
    public Map<String, Object> createOrder(
            @RequestParam(defaultValue = "0") long paymentDelayMs,
            @RequestParam(defaultValue = "false") boolean paymentFail,
            @RequestParam(defaultValue = "false") boolean paymentClientError,
            @RequestParam(defaultValue = "0") long inventoryDelayMs,
            @RequestParam(defaultValue = "false") boolean inventoryFail,
            @RequestParam(defaultValue = "false") boolean inventoryClientError) {
        // Defaults keep GET /orders exactly as the discovery lab left it.
        // The extra parameters only exist so a circuit-breaker experiment can force a fault.
        return Map.of(
                "order", identity(),
                "inventory", inventoryClient.callInventory(inventoryDelayMs, inventoryFail, inventoryClientError),
                "payment", paymentClient.callPayment(paymentDelayMs, paymentFail, paymentClientError));
    }

    private Map<String, String> identity() {
        return Map.of(
                "service", "order-service",
                "container", containerId,
                "ip", localIp(),
                "port", "8080");
    }

    private static String localIp() {
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = nic.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignored) {
            return "unknown";
        }
        return "unknown";
    }
}
