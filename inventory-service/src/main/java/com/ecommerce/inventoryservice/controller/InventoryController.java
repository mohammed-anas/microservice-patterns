package com.ecommerce.inventoryservice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Map;

/** Author: Mohammed Anas Date: 09/11/2026 */
@RestController
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    @Value("${HOSTNAME:local}")
    private String containerId;

    @GetMapping("/inventory")
    public Map<String, String> inventory(
            @RequestParam(defaultValue = "0") long delayMs,
            @RequestParam(defaultValue = "false") boolean fail,
            @RequestParam(defaultValue = "false") boolean clientError) {
        applyLabFault(delayMs, fail, clientError);
        return Map.of(
                "service", "inventory-service",
                "container", containerId,
                "ip", localIp(),
                "port", "8080",
                "message", "Stock reserved");
    }

    private static void applyLabFault(long delayMs, boolean fail, boolean clientError) {
        long wait = Math.min(Math.max(delayMs, 0), 8_000);
        if (wait > 0 || fail || clientError) {
            log.info("lab fault delayMs={} fail={} clientError={}", wait, fail, clientError);
        }
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "interrupted");
            }
        }
        if (clientError) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "forced client error");
        }
        if (fail) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "forced failure");
        }
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
