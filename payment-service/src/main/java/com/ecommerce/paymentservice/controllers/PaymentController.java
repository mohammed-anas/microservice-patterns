package com.ecommerce.paymentservice.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Map;

/** Author: Mohammed Anas Date: 09/11/2026 */
@RestController
public class PaymentController {

    @Value("${HOSTNAME:local}")
    private String containerId;

    @GetMapping("/payments")
    public Map<String, String> payment() {
        return Map.of(
                "service", "payment-service",
                "container", containerId,
                "ip", localIp(),
                "port", "8080",
                "message", "Payment processed");
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
