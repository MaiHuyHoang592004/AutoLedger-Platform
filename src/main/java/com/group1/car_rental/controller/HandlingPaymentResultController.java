package com.group1.car_rental.controller;

import com.group1.car_rental.config.Config;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Controller
public class HandlingPaymentResultController {

    @GetMapping("/handle-payment-result")
    public RedirectView handlePayment(@RequestParam Map<String,String> params) throws Exception {
        Map<String,String> fields = new HashMap<>();
        for (Map.Entry<String,String> entry : params.entrySet()) {
            String key = URLEncoder.encode(entry.getKey(), StandardCharsets.US_ASCII.toString());
            String value = URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII.toString());
            if (value != null && !value.isEmpty()) {
                fields.put(key, value);
            }
        }

        String vnp_SecureHash = params.get("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");
        fields.remove("vnp_SecureHash");

        String isSuccess = "failed";
        String signValue = Config.hashAllFields(fields);

        if (signValue.equals(vnp_SecureHash)) {
            if ("00".equals(params.get("vnp_TransactionStatus"))) {
                isSuccess = "success";
                // new BookingDAO().updateBookingStatus(Config.orderID, "Submitted");
            } else {
                // new BookingDAO().updateBookingStatus(Config.orderID, "Wait for pay");
            }
        } else {
            // new BookingDAO().updateBookingStatus(Config.orderID, "Wait for pay");
        }

        return new RedirectView("/payment/payment-result?status=" + isSuccess);
    }
}
