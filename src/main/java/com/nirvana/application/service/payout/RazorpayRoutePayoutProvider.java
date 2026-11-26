package com.nirvana.application.service.payout;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Pays spas through Razorpay Route: each spa is a linked account, and a payout is one direct transfer
 * (POST /v1/transfers) from Nirvana's balance. Direct transfers must be enabled on the Razorpay account.
 * Transfers go over plain HTTPS rather than the SDK: the SDK keeps extra headers in a static map shared by
 * every client, so the idempotency header would leak onto checkout calls.
 */
@Slf4j
public class RazorpayRoutePayoutProvider implements PayoutProvider {
    public static final String NAME = "RAZORPAY_ROUTE";
    private static final URI API = URI.create("https://api.razorpay.com/v1/transfers");
    private final RazorpayClient client;
    private final PayoutProperties props;
    private final String auth;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public RazorpayRoutePayoutProvider(RazorpayClient client, String keyId, String keySecret, PayoutProperties props) {
        this.client = client;
        this.props = props;
        this.auth = "Basic " + Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
    }

    private JSONObject call(HttpRequest.Builder request) throws IOException, InterruptedException {
        var response = http.send(request.header("Authorization", auth).timeout(Duration.ofSeconds(30)).build(),
                HttpResponse.BodyHandlers.ofString());
        JSONObject body;
        try {
            body = response.body() == null || response.body().isBlank() ? new JSONObject() : new JSONObject(response.body());
        } catch (org.json.JSONException e) {
            body = new JSONObject();
        }
        // A 5xx may or may not have made the transfer: leave it unknown (IOException), not refused.
        if (response.statusCode() >= 500) throw new IOException("Razorpay error " + response.statusCode());
        if (response.statusCode() >= 400) {
            var error = body.optJSONObject("error");
            throw new Refused(error == null ? "HTTP " + response.statusCode() : error.optString("description", "HTTP " + response.statusCode()));
        }
        return body;
    }

    /** Razorpay answered and said no: nothing was transferred. */
    static class Refused extends IOException {
        Refused(String message) { super(message); }
    }

    @Override public String name() { return NAME; }
    @Override public boolean automatic() { return true; }

    @Override
    public Transfer transfer(TransferRequest r) {
        if (!"INR".equalsIgnoreCase(r.currency())) return Transfer.failed("Razorpay transfers are in INR only");
        if (r.amountCents() < 100) return Transfer.failed("Razorpay transfers start at Rs 1");
        var body = new JSONObject().put("account", r.linkedAccountId()).put("amount", r.amountCents()).put("currency", "INR")
                .put("notes", new JSONObject().put("payout_reference", r.reference()).put("spa_id", String.valueOf(r.spaId())));
        try {
            // A retry of the same attempt reuses the key, so Razorpay returns the first transfer instead of making a second.
            return read(call(HttpRequest.newBuilder(API).header("Content-Type", "application/json")
                    .header("X-Transfer-Idempotency", r.idempotencyKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))));
        } catch (Refused e) {
            log.warn("event=payout.transfer_failed payout={} reason={}", r.payoutId(), e.getMessage());
            return Transfer.failed(trim(e.getMessage()));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            // Unknown whether Razorpay got it: leave the payout in progress. A retry reuses the same key, so
            // Razorpay returns the transfer it made, if any, instead of making a second one.
            log.warn("event=payout.transfer_unknown payout={} reason={}", r.payoutId(), e.getMessage());
            return new Transfer(State.IN_PROGRESS, null, "No answer from Razorpay; check before retrying");
        }
    }

    @Override
    public Transfer status(String transferId) {
        try {
            var t = read(call(HttpRequest.newBuilder(URI.create(API + "/" + transferId)).GET()));
            return new Transfer(t.state(), transferId, t.message());
        } catch (IOException e) {
            return new Transfer(State.IN_PROGRESS, transferId, trim(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Transfer(State.IN_PROGRESS, transferId, null);
        }
    }

    static Transfer read(JSONObject t) {
        String status = t.optString("status", t.optString("transfer_status", "created")).toLowerCase();
        String id = t.optString("id", null);
        var error = t.optJSONObject("error");
        String reason = error == null ? null : error.optString("description", error.optString("reason", null));
        return switch (status) {
            case "processed", "settled" -> new Transfer(State.COMPLETED, id, null);
            case "failed", "reversed", "partially_reversed" -> new Transfer(State.FAILED, id, reason == null ? "Transfer " + status : trim(reason));
            default -> new Transfer(State.IN_PROGRESS, id, null);
        };
    }

    @Override
    public synchronized Link link(LinkRequest r) {
        String account = r.existingAccountId(), product = r.existingProductId();
        try {
            if (account == null) {
                var address = new JSONObject().put("street1", r.street1()).put("street2", r.street2() == null ? r.city() : r.street2())
                        .put("city", r.city()).put("state", r.state()).put("postal_code", r.postalCode()).put("country", "IN");
                var body = new JSONObject().put("email", r.email()).put("phone", r.phone()).put("type", "route")
                        .put("reference_id", "spa-" + r.spaId()).put("legal_business_name", r.businessName())
                        .put("business_type", r.businessType()).put("contact_name", r.contactName())
                        .put("profile", new JSONObject().put("category", props.getRouteCategory())
                                .put("subcategory", props.getRouteSubcategory())
                                .put("addresses", new JSONObject().put("registered", address)));
                account = client.account.create(body).toJson().getString("id");
                client.stakeholder.create(account, new JSONObject().put("name", r.contactName()).put("email", r.email()));
            }
            if (product == null)
                product = client.product.requestProductConfiguration(account,
                        new JSONObject().put("product_name", "route").put("tnc_accepted", true)).toJson().getString("id");
            client.product.edit(account, product, new JSONObject().put("tnc_accepted", true)
                    .put("settlements", new JSONObject().put("account_number", r.accountNumber())
                            .put("ifsc_code", r.ifsc()).put("beneficiary_name", r.accountHolder())));
            return new Link(account, product, null);
        } catch (RazorpayException | RuntimeException e) {
            log.warn("event=payout.link_failed spa={} reason={}", r.spaId(), e.getMessage());
            return new Link(account, product, trim(e.getMessage()));
        }
    }

    private static String trim(String s) {
        if (s == null) return "Razorpay did not say why";
        return s.length() > 480 ? s.substring(0, 480) : s;
    }
}
